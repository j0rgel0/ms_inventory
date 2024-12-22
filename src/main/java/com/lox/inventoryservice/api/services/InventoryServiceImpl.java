package com.lox.inventoryservice.api.services;

import com.lox.inventoryservice.api.exceptions.InsufficientStockException;
import com.lox.inventoryservice.api.exceptions.InventoryNotFoundException;
import com.lox.inventoryservice.api.kafka.events.Event;
import com.lox.inventoryservice.api.kafka.events.EventType;
import com.lox.inventoryservice.api.kafka.events.InventoryAddedEvent;
import com.lox.inventoryservice.api.kafka.events.InventoryReleasedEvent;
import com.lox.inventoryservice.api.kafka.events.InventoryRemovedEvent;
import com.lox.inventoryservice.api.kafka.events.InventoryReserveFailedEvent;
import com.lox.inventoryservice.api.kafka.events.InventoryReservedEvent;
import com.lox.inventoryservice.api.kafka.events.InventoryUpdatedEvent;
import com.lox.inventoryservice.api.kafka.topics.KafkaTopics;
import com.lox.inventoryservice.api.models.Inventory;
import com.lox.inventoryservice.api.models.Product;
import com.lox.inventoryservice.api.models.dto.OrderCreatedEventDTO;
import com.lox.inventoryservice.api.models.dto.OrderItemDTO;
import com.lox.inventoryservice.api.models.dto.ReasonDetail;
import com.lox.inventoryservice.api.models.dto.ReservedItemEvent;
import com.lox.inventoryservice.api.models.page.InventoryPage;
import com.lox.inventoryservice.api.models.responses.InventoryResponse;
import com.lox.inventoryservice.api.repositories.r2dbc.InventoryRepository;
import com.lox.inventoryservice.common.kafka.event.EventProducer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final EventProducer eventProducer;
    private final ReactiveRedisTemplate<String, Inventory> reactiveRedisTemplate;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final WebClient productCatalogWebClient;
    private ReactiveHashOperations<String, String, Inventory> hashOps;

    private static final String HASH_KEY = "inventoryCache";

    @PostConstruct
    public void init() {
        log.info("Initializing InventoryServiceImpl and setting up Redis hash operations.");
        this.hashOps = reactiveRedisTemplate.opsForHash();
    }

    @PostConstruct
    public void clearInventoryCacheOnStartup() {
        log.info("Flushing Redis cache for key: {}", HASH_KEY);
        reactiveRedisTemplate.delete(HASH_KEY)
                .doOnSuccess(success -> log.info("Redis cache flushed successfully on startup."))
                .doOnError(
                        error -> log.error("Failed to flush Redis cache: {}", error.getMessage()))
                .subscribe();
    }

    /**
     * Helper method to publish events to both INVENTORY_STATUS_EVENTS_TOPIC and
     * NOTIFICATIONS_EVENTS_TOPIC.
     *
     * @param event     The event object to be published.
     * @param eventName A descriptive name of the event for logging purposes.
     * @return A Mono that completes when both publications are done.
     */
    private Mono<Void> publishToBothTopics(Event event, String eventName) {
        return Mono.when(
                eventProducer.publishEvent(KafkaTopics.INVENTORY_STATUS_EVENTS_TOPIC, event)
                        .doOnSuccess(aVoid -> log.info("Published {} event to topic '{}'",
                                eventName, KafkaTopics.INVENTORY_STATUS_EVENTS_TOPIC)),
                eventProducer.publishEvent(KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC, event)
                        .doOnSuccess(aVoid -> log.info("Published {} event to topic '{}'",
                                eventName, KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC))
        ).then();
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackCreateInventory")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackCreateInventory")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackCreateInventory")
    public Mono<InventoryResponse> createInventory(Inventory inventory) {
        log.info("Entered createInventory with Inventory: {}", inventory);

        if (inventory.getAvailableQuantity() == null || inventory.getAvailableQuantity() < 1) {
            log.info("Invalid available quantity: {}", inventory.getAvailableQuantity());
            return Mono.error(
                    new IllegalArgumentException("Available quantity must be greater than 0."));
        }

        UUID productId = inventory.getProductId();
        if (productId == null) {
            log.info("Product ID is null in inventory: {}", inventory);
            return Mono.error(new IllegalArgumentException("Product ID must be provided."));
        }

        log.info("Creating inventory for Product ID: {}", productId);

        return inventoryRepository.findByProductId(productId)
                .flatMap(existingInventory -> {
                    log.warn("Inventory already exists for Product ID: {}. Skipping creation.",
                            productId);
                    // Specify <InventoryResponse> in Mono.error to maintain the type
                    return Mono.<InventoryResponse>error(new IllegalStateException(
                            "Inventory already exists for Product ID: " + productId));
                })
                .switchIfEmpty(
                        productCatalogWebClient.get()
                                .uri("/api/products/{productId}", productId)
                                .retrieve()
                                .bodyToMono(Product.class)
                                .doOnSubscribe(sub -> log.info(
                                        "Fetching product details for Product ID: {}", productId))
                                .doOnNext(product -> log.info("Product found: {}", product))
                                .flatMap(product -> {
                                    inventory.setInventoryId(UUID.randomUUID());
                                    inventory.setReservedQuantity(0);
                                    inventory.setLastUpdated(Instant.now());
                                    log.info("Initialized Inventory: {}", inventory);

                                    return r2dbcEntityTemplate.insert(Inventory.class)
                                            .using(inventory)
                                            .doOnSubscribe(sub -> log.info(
                                                    "Inserting Inventory into the database: {}",
                                                    inventory))
                                            .doOnSuccess(savedInventory -> log.info(
                                                    "Inventory inserted successfully: {}",
                                                    savedInventory))
                                            .flatMap(savedInventory -> {
                                                InventoryAddedEvent event = InventoryAddedEvent.fromInventory(
                                                        savedInventory);
                                                return publishToBothTopics(event, "INVENTORY_ADDED")
                                                        .thenReturn(savedInventory);
                                            })
                                            .flatMap(savedInventory ->
                                                    hashOps.put(HASH_KEY,
                                                                    savedInventory.getProductId()
                                                                            .toString(), savedInventory)
                                                            .doOnSuccess(aBoolean -> log.info(
                                                                    "Cached Inventory ID: {} in Redis under key: {}",
                                                                    savedInventory.getInventoryId(),
                                                                    savedInventory.getProductId()))
                                                            // Return the Inventory object to the next operator
                                                            .thenReturn(savedInventory)
                                            )
                                            // Transform Inventory into InventoryResponse
                                            .flatMap(this::fetchProductAndBuildResponse)
                                            .doOnSuccess(invResponse -> log.info(
                                                    "Inventory created and cached for Product ID: {}",
                                                    invResponse.getInventory().getProductId()))
                                            .doOnError(
                                                    e -> log.error("Error creating inventory: {}",
                                                            e.getMessage()));
                                })
                )
                // Specify <InventoryResponse> in onErrorResume to maintain the type
                .onErrorResume(e -> {
                    log.error("Product not found or error occurred: {}", e.getMessage());
                    return Mono.<InventoryResponse>error(
                            new RuntimeException("Product does not exist or an error occurred."));
                })
                .doOnTerminate(
                        () -> log.info("Exiting createInventory for Product ID: {}", productId));
    }


    @Override
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackGetInventoryByProductId")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackGetInventoryByProductId")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackGetInventoryByProductId")
    public Mono<InventoryResponse> getInventoryByProductId(UUID productId) {
        log.info("Entered getInventoryByProductId with Product ID: {}", productId);
        String key = productId.toString();

        return hashOps.get(HASH_KEY, key)
                .doOnSubscribe(subscription -> log.info("Checking Redis cache for Product ID: {}",
                        productId))
                .flatMap(cachedInventory -> {
                    if (cachedInventory != null) {
                        log.info("Inventory retrieved from cache for Product ID: {}", productId);
                        return fetchProductAndBuildResponse(cachedInventory);
                    } else {
                        log.info("No cached inventory found for Product ID: {}", productId);
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(
                        inventoryRepository.findByProductId(productId)
                                .doOnSubscribe(subscription -> log.info(
                                        "Fetching Inventory from database for Product ID: {}",
                                        productId))
                                .switchIfEmpty(Mono.error(new InventoryNotFoundException(
                                        "Inventory not found for Product ID: " + productId)))
                                .flatMap(inventory -> {
                                    log.info("Inventory found in database: {}", inventory);
                                    return hashOps.put(HASH_KEY,
                                                    inventory.getProductId().toString(), inventory)
                                            .doOnSuccess(aBoolean -> log.info(
                                                    "Cached Inventory ID: {} in Redis under key: {}",
                                                    inventory.getInventoryId(),
                                                    inventory.getProductId()))
                                            .thenReturn(inventory);
                                })
                                .flatMap(this::fetchProductAndBuildResponse)
                )
                .doOnNext(invResponse -> log.info("Inventory retrieved for Product ID: {}",
                        invResponse.getInventory().getProductId()))
                .doOnTerminate(() -> log.info("Exiting getInventoryByProductId for Product ID: {}",
                        productId));
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackUpdateInventory")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackUpdateInventory")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackUpdateInventory")
    public Mono<InventoryResponse> updateInventory(UUID productId, Inventory inventory) {
        log.info("Entered updateInventory for Product ID: {} with Inventory: {}", productId,
                inventory);

        return inventoryRepository.findByProductId(productId)
                .doOnSubscribe(subscription -> log.info(
                        "Fetching existing Inventory from database for Product ID: {}", productId))
                .switchIfEmpty(Mono.error(new InventoryNotFoundException(
                        "Inventory not found for Product ID: " + productId)))
                .flatMap(existingInventory -> {
                    existingInventory.setAvailableQuantity(inventory.getAvailableQuantity());
                    existingInventory.setReorderLevel(inventory.getReorderLevel());
                    existingInventory.setReorderQuantity(inventory.getReorderQuantity());
                    existingInventory.setLastUpdated(Instant.now());
                    log.info("Updated Inventory fields: {}", existingInventory);

                    return r2dbcEntityTemplate.update(Inventory.class)
                            .matching(Query.query(Criteria.where("product_id").is(productId)))
                            .apply(Update.update("available_quantity",
                                            existingInventory.getAvailableQuantity())
                                    .set("reorder_level", existingInventory.getReorderLevel())
                                    .set("reorder_quantity", existingInventory.getReorderQuantity())
                                    .set("last_updated", existingInventory.getLastUpdated()))
                            .doOnSubscribe(subscription -> log.info(
                                    "Updating Inventory in the database for Product ID: {}",
                                    productId))
                            .doOnSuccess(rowsUpdated -> log.info(
                                    "Database update completed for Product ID: {}", productId))
                            .thenReturn(existingInventory);
                })
                .flatMap(updatedInventory -> {
                    InventoryUpdatedEvent event = InventoryUpdatedEvent.fromInventory(
                            updatedInventory);
                    return publishToBothTopics(event, "INVENTORY_UPDATED")
                            .thenReturn(updatedInventory);
                })
                .flatMap(updatedInventory ->
                        hashOps.put(HASH_KEY, updatedInventory.getProductId().toString(),
                                        updatedInventory)
                                .doOnSuccess(aBoolean -> log.info(
                                        "Cached updated Inventory ID: {} in Redis under key: {}",
                                        updatedInventory.getInventoryId(),
                                        updatedInventory.getProductId()))
                                .thenReturn(updatedInventory)
                )
                .flatMap(this::fetchProductAndBuildResponse)
                .doOnSuccess(invResponse -> log.info(
                        "Inventory updated and cache refreshed for Product ID: {}",
                        invResponse.getInventory().getProductId()))
                .doOnError(e -> log.error("Error updating inventory: {}", e.getMessage()))
                .doOnTerminate(
                        () -> log.info("Exiting updateInventory for Product ID: {}", productId));
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackDeleteInventory")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackDeleteInventory")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackDeleteInventory")
    public Mono<Void> deleteInventory(UUID productId) {
        log.info("Entered deleteInventory for Product ID: {}", productId);
        String key = productId.toString();

        return inventoryRepository.findByProductId(productId)
                .doOnSubscribe(subscription -> log.info(
                        "Fetching Inventory from database for deletion for Product ID: {}",
                        productId))
                .switchIfEmpty(Mono.error(new InventoryNotFoundException(
                        "Inventory not found for Product ID: " + productId)))
                .flatMap(existingInventory ->
                        r2dbcEntityTemplate.delete(Inventory.class)
                                .matching(Query.query(Criteria.where("product_id").is(productId)))
                                .all()
                                .doOnSubscribe(subscription -> log.info(
                                        "Deleting Inventory from database for Product ID: {}",
                                        productId))
                                .doOnSuccess(deletedCount -> log.info(
                                        "Deleted {} records from database for Product ID: {}",
                                        deletedCount, productId))
                                .then(publishToBothTopics(
                                        InventoryRemovedEvent.fromProductId(productId),
                                        "INVENTORY_REMOVED"))
                                .then(hashOps.remove(HASH_KEY, key)
                                        .doOnSuccess(aBoolean -> log.info(
                                                "Removed Inventory from Redis cache for Product ID: {}",
                                                productId))
                                ))
                .then()
                .doOnSuccess(v -> log.info("Inventory deleted and cache removed for Product ID: {}",
                        productId))
                .doOnError(e -> log.error("Error deleting inventory: {}", e.getMessage()))
                .doOnTerminate(
                        () -> log.info("Exiting deleteInventory for Product ID: {}", productId));
    }

    @Override
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackListInventory")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackListInventory")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackListInventory")
    public Mono<InventoryPage> listInventory(List<String> filters, Pageable pageable) {
        log.info("Entered listInventory with filters: {} and pageable: {}", filters, pageable);

        Boolean reorderNeeded = null;
        if (filters != null) {
            for (String filter : filters) {
                if ("reorderNeeded=true".equalsIgnoreCase(filter)) {
                    reorderNeeded = true;
                    break;
                } else if ("reorderNeeded=false".equalsIgnoreCase(filter)) {
                    reorderNeeded = false;
                    break;
                }
            }
        }
        log.info("Determined reorderNeeded filter as: {}", reorderNeeded);

        Mono<Long> totalElementsMono = reorderNeeded != null
                ? inventoryRepository.findAllByReorderNeeded(reorderNeeded).count()
                : inventoryRepository.count();
        log.info("Total elements count Mono initialized.");

        Mono<List<Inventory>> inventoryMono = reorderNeeded != null
                ? inventoryRepository.findAllByReorderNeeded(reorderNeeded)
                .skip(pageable.getOffset())
                .take(pageable.getPageSize()).collectList()
                : inventoryRepository.findAll().skip(pageable.getOffset())
                        .take(pageable.getPageSize()).collectList();
        log.info("Inventory Mono initialized with pagination.");

        return Mono.zip(totalElementsMono, inventoryMono)
                .doOnSubscribe(subscription -> log.info(
                        "Fetching inventory list with total elements and paginated results."))
                .flatMap(tuple -> {
                    long totalElements = tuple.getT1();
                    List<Inventory> inventories = tuple.getT2();
                    int totalPages = (int) Math.ceil(
                            (double) totalElements / pageable.getPageSize());
                    int currentPage = pageable.getPageNumber();
                    int pageSize = pageable.getPageSize();

                    log.info("Total Elements: {}, Total Pages: {}, Current Page: {}, Page Size: {}",
                            totalElements, totalPages, currentPage, pageSize);

                    return Flux.fromIterable(inventories)
                            .flatMap(this::fetchProductAndBuildResponse)
                            .collectList()
                            .map(inventoryResponses -> {
                                log.info("Collected InventoryResponses for Inventory Page.");
                                return new InventoryPage(
                                        inventoryResponses,
                                        totalElements,
                                        totalPages,
                                        currentPage,
                                        pageSize
                                );
                            });
                })
                .doOnSuccess(page -> log.info("Retrieved {} inventories out of {}",
                        page.getInventories().size(), page.getTotalElements()))
                .doOnError(e -> log.error("Error retrieving inventory page: {}", e.getMessage()))
                .doOnTerminate(() -> log.info("Exiting listInventory."));
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackReserveInventory")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackReserveInventory")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackReserveInventory")
    public Mono<InventoryResponse> reserveInventory(UUID productId, Integer quantity) {
        log.info("Entered reserveInventory for Product ID: {} with Quantity: {}", productId,
                quantity);

        return inventoryRepository.findByProductId(productId)
                .doOnSubscribe(subscription -> log.info(
                        "Fetching Inventory from DB for Product ID: {}", productId))
                .switchIfEmpty(Mono.error(new InventoryNotFoundException(
                        "Inventory not found for Product ID: " + productId)))
                .flatMap(inventory -> {
                    if (inventory.getAvailableQuantity() < quantity) {
                        log.warn(
                                "Insufficient stock for Product ID: {}. Available: {}, Requested: {}",
                                productId, inventory.getAvailableQuantity(), quantity);
                        return Mono.error(
                                new InsufficientStockException("Insufficient stock available."));
                    }

                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
                    inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
                    inventory.setLastUpdated(Instant.now());
                    log.info("Updated Inventory for reservation: {}", inventory);

                    return r2dbcEntityTemplate.update(Inventory.class)
                            .matching(Query.query(Criteria.where("product_id").is(productId)))
                            .apply(Update.update("available_quantity",
                                            inventory.getAvailableQuantity())
                                    .set("reserved_quantity", inventory.getReservedQuantity())
                                    .set("last_updated", inventory.getLastUpdated()))
                            .doOnSubscribe(sub -> log.info(
                                    "Updating Inventory in DB for reservation (Product ID: {})",
                                    productId))
                            .doOnSuccess(rows -> log.info(
                                    "DB update completed for reservation (Product ID: {})",
                                    productId))
                            .thenReturn(inventory);
                })
                .flatMap(updatedInventory -> {
                    InventoryReservedEvent event = InventoryReservedEvent.builder()
                            .eventType(EventType.INVENTORY_RESERVED.name())
                            .orderId(null)  // or pass an actual orderId if you have it
                            .items(List.of())  // or populate with actual items as needed
                            .timestamp(Instant.now())
                            .build();

                    return publishToBothTopics(event, "INVENTORY_RESERVED")
                            .thenReturn(updatedInventory);
                })

                .flatMap(updatedInventory ->
                        hashOps.put(HASH_KEY, updatedInventory.getProductId().toString(),
                                        updatedInventory)
                                .doOnSuccess(bool -> log.info(
                                        "Cached Inventory ID: {} in Redis (key: {})",
                                        updatedInventory.getInventoryId(),
                                        updatedInventory.getProductId()))
                                .thenReturn(updatedInventory)
                )
                .flatMap(this::fetchProductAndBuildResponse)
                .doOnSuccess(invResponse -> log.info(
                        "Inventory reserved and cache refreshed for Product ID: {}",
                        invResponse.getInventory().getProductId()))
                .doOnError(e -> log.error("Error reserving inventory: ", e))
                .doOnTerminate(
                        () -> log.info("Exiting reserveInventory for Product ID: {}", productId));
    }


    @Override
    @Transactional
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackReleaseInventory")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackReleaseInventory")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackReleaseInventory")
    public Mono<InventoryResponse> releaseInventory(UUID productId, Integer quantity) {
        log.info("Entered releaseInventory for Product ID: {} with Quantity: {}", productId,
                quantity);

        return inventoryRepository.findByProductId(productId)
                .doOnSubscribe(subscription -> log.info(
                        "Fetching Inventory from database for release for Product ID: {}",
                        productId))
                .switchIfEmpty(Mono.error(new InventoryNotFoundException(
                        "Inventory not found for Product ID: " + productId)))
                .flatMap(inventory -> {
                    if (inventory.getReservedQuantity() < quantity) {
                        log.warn(
                                "Cannot release more than reserved for Product ID: {}. Reserved: {}, Requested: {}",
                                productId, inventory.getReservedQuantity(), quantity);
                        return Mono.error(new RuntimeException(
                                "Cannot release more than reserved quantity."));
                    }

                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
                    inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
                    inventory.setLastUpdated(Instant.now());
                    log.info("Updated Inventory for release: {}", inventory);

                    return r2dbcEntityTemplate.update(Inventory.class)
                            .matching(Query.query(Criteria.where("product_id").is(productId)))
                            .apply(Update.update("available_quantity",
                                            inventory.getAvailableQuantity())
                                    .set("reserved_quantity", inventory.getReservedQuantity())
                                    .set("last_updated", inventory.getLastUpdated()))
                            .doOnSubscribe(subscription -> log.info(
                                    "Updating Inventory in the database for release for Product ID: {}",
                                    productId))
                            .doOnSuccess(rowsUpdated -> log.info(
                                    "Database update completed for release of Product ID: {}",
                                    productId))
                            .thenReturn(inventory);
                })
                .flatMap(updatedInventory -> {
                    InventoryReleasedEvent event = InventoryReleasedEvent.fromInventory(
                            updatedInventory);
                    return publishToBothTopics(event, "INVENTORY_RELEASED")
                            .thenReturn(updatedInventory);
                })
                .flatMap(updatedInventory ->
                        hashOps.put(HASH_KEY, updatedInventory.getProductId().toString(),
                                        updatedInventory)
                                .doOnSuccess(aBoolean -> log.info(
                                        "Cached released Inventory ID: {} in Redis under key: {}",
                                        updatedInventory.getInventoryId(),
                                        updatedInventory.getProductId()))
                                .thenReturn(updatedInventory)
                )
                .flatMap(this::fetchProductAndBuildResponse)
                .doOnSuccess(invResponse -> log.info(
                        "Inventory released and cache refreshed for Product ID: {}",
                        invResponse.getInventory().getProductId()))
                .doOnError(e -> log.error("Error releasing inventory: {}", e.getMessage()))
                .doOnTerminate(
                        () -> log.info("Exiting releaseInventory for Product ID: {}", productId));
    }

    private Mono<InventoryResponse> fetchProductAndBuildResponse(Inventory inventory) {
        UUID productId = inventory.getProductId();
        log.info("Fetching product details for Product ID: {}", productId);

        return productCatalogWebClient.get()
                .uri("/api/products/{productId}", productId)
                .retrieve()
                .bodyToMono(Product.class)
                .map(product -> InventoryResponse.builder()
                        .inventory(inventory)
                        .product(product)
                        .build())
                .doOnSuccess(invResponse -> log.info(
                        "InventoryResponse built successfully for Product ID: {}", productId))
                .onErrorResume(e -> {
                    log.error("Error fetching product details for Product ID {}: {}", productId,
                            e.getMessage());
                    return Mono.error(new RuntimeException("Failed to fetch product details."));
                });
    }


    @KafkaListener(
            topics = "inventory.commands",
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public Mono<Void> handleOrderCreatedEvent(OrderCreatedEventDTO event) {
        UUID trackId = event.getTrackId();
        UUID orderId = event.getOrderId();
        log.info("Received OrderCreatedEvent for Order ID: {}", orderId);

        List<OrderItemDTO> items = event.getItems();
        List<ReasonDetail> reasonDetails = new ArrayList<>();

        // ---- PASS 1: Validate all items, collect errors if any ----
        return Flux.fromIterable(items)
                .flatMap(item -> validateSingleItem(item, reasonDetails))
                .then(Mono.defer(() -> {
                    if (!reasonDetails.isEmpty()) {
                        // Found at least one failing product => publish error, skip reservation
                        InventoryReserveFailedEvent failedEvent = InventoryReserveFailedEvent.builder()
                                .eventType(EventType.INVENTORY_RESERVE_FAILED.name())
                                .trackId(trackId)
                                .orderId(orderId)
                                .reasons(reasonDetails)
                                .timestamp(Instant.now())
                                .build();
                        // Publish to both topics
                        return publishToBothTopics(failedEvent, "INVENTORY_RESERVE_FAILED").then();
                    }

                    // ---- PASS 2: Everything is OK => update DB + build extended items ----
                    List<ReservedItemEvent> extendedItems = new ArrayList<>();

                    return Flux.fromIterable(items)
                            .flatMap(item -> reserveSingleItem(item, extendedItems))
                            .then(Mono.defer(() -> {
                                // Compute orderTotal from extendedItems
                                double total = extendedItems.stream()
                                        .mapToDouble(ReservedItemEvent::getTotalPrice)
                                        .sum();
                                InventoryReservedEvent reservedEvent = InventoryReservedEvent.builder()
                                        .eventType(EventType.INVENTORY_RESERVED.name())
                                        .trackId(trackId)
                                        .orderId(orderId)
                                        .items(extendedItems)        // with price info
                                        .orderTotal(total)           // total for entire order
                                        .timestamp(Instant.now())
                                        .build();

                                // Publish to both topics
                                return publishToBothTopics(reservedEvent,
                                        "INVENTORY_RESERVED").then();
                            }));
                }))
                .doOnSuccess(x -> log.info("Order {} processed successfully.", orderId))
                .doOnError(
                        e -> log.error("Error processing order {}: {}", orderId, e.getMessage()));
    }

    /**
     * Validate a single item: - If there's no inventory record => reason "No inventory record" - If
     * availableQuantity = 0 => reason "No available inventory" - If availableQuantity < requested
     * => reason "Requested X but only Y available" - Otherwise OK Appends errors to reasonDetails
     * if any.
     */
    private Mono<Void> validateSingleItem(OrderItemDTO item, List<ReasonDetail> reasonDetails) {
        UUID productId = item.getProductId();
        Integer requestedQty = item.getQuantity();

        return inventoryRepository.findByProductId(productId)
                .flatMap(inventory -> {
                    if (inventory.getAvailableQuantity() <= 0) {
                        return fetchProduct(productId).map(product -> {
                            reasonDetails.add(
                                    ReasonDetail.builder()
                                            .productId(productId)
                                            .productName(product.getName())
                                            .message("No available inventory for this product.")
                                            .build()
                            );
                            return "";
                        });
                    } else if (inventory.getAvailableQuantity() < requestedQty) {
                        return fetchProduct(productId).map(product -> {
                            reasonDetails.add(
                                    ReasonDetail.builder()
                                            .productId(productId)
                                            .productName(product.getName())
                                            .message(String.format(
                                                    "Requested %d but only %d available.",
                                                    requestedQty, inventory.getAvailableQuantity()
                                            ))
                                            .build()
                            );
                            return "";
                        });
                    }
                    // If we reach here, it's valid => no error
                    return Mono.just("");
                })
                .switchIfEmpty(
                        // Means we have no Inventory record => reason
                        fetchProduct(productId).map(product -> {
                            reasonDetails.add(
                                    ReasonDetail.builder()
                                            .productId(productId)
                                            .productName(product.getName())
                                            .message("No inventory record for this product.")
                                            .build()
                            );
                            return "";
                        })
                )
                .then(); // Return Mono<Void>
    }

    /**
     * Reserve a single item => update DB => build a ReservedItemEvent with product price
     */
    private Mono<Void> reserveSingleItem(OrderItemDTO item, List<ReservedItemEvent> extendedItems) {
        UUID productId = item.getProductId();
        Integer requestedQty = item.getQuantity();

        // 1) Fetch inventory & product in parallel
        Mono<Inventory> inventoryMono = inventoryRepository.findByProductId(productId);
        Mono<Product> productMono = fetchProduct(productId);

        return Mono.zip(inventoryMono, productMono)
                .flatMap(tuple -> {
                    Inventory inventory = tuple.getT1();
                    Product product = tuple.getT2();

                    // 2) Update inventory
                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() - requestedQty);
                    inventory.setReservedQuantity(inventory.getReservedQuantity() + requestedQty);
                    inventory.setLastUpdated(Instant.now());

                    // 3) Build extended item with price info
                    double unitPrice = (product.getPrice() != null)
                            ? product.getPrice().doubleValue()
                            : 0.0;
                    double totalPrice = unitPrice * requestedQty;

                    ReservedItemEvent reservedItem = ReservedItemEvent.builder()
                            .productId(productId)
                            .quantity(requestedQty)
                            .unitPrice(unitPrice)
                            .totalPrice(totalPrice)
                            .build();

                    // 4) Persist DB updates first
                    return r2dbcEntityTemplate.update(Inventory.class)
                            .matching(Query.query(Criteria.where("product_id").is(productId)))
                            .apply(
                                    Update.update("available_quantity",
                                                    inventory.getAvailableQuantity())
                                            .set("reserved_quantity",
                                                    inventory.getReservedQuantity())
                                            .set("last_updated", inventory.getLastUpdated())
                            )
                            .thenReturn(reservedItem);
                })
                .doOnNext(extendedItems::add)
                .then();
    }

    /**
     * Fetch the full Product details (including price) from product-catalog.
     */
    private Mono<Product> fetchProduct(UUID productId) {
        return productCatalogWebClient
                .get()
                .uri("/api/products/{productId}", productId)
                .retrieve()
                .bodyToMono(Product.class)
                .onErrorResume(e -> {
                    log.error("Error fetching product details for {}: {}", productId,
                            e.getMessage());
                    // Return a product with placeholder name/price
                    Product fallback = Product.builder()
                            .productId(productId)
                            .name("Unknown Product")
                            .build();
                    return Mono.just(fallback);
                });
    }

    // Fallback Methods

    public Mono<InventoryResponse> fallbackCreateInventory(Inventory inventory,
            Throwable throwable) {
        log.error("Fallback triggered for createInventory due to: {}", throwable.getMessage());
        return Mono.error(new RuntimeException(
                "Inventory creation is currently unavailable. Please try again later."));
    }

    public Mono<InventoryResponse> fallbackGetInventoryByProductId(UUID productId,
            Throwable throwable) {
        log.error("Fallback triggered for getInventoryByProductId due to: {}",
                throwable.getMessage());
        return Mono.error(new RuntimeException(
                "Inventory retrieval is currently unavailable. Please try again later."));
    }

    public Mono<InventoryResponse> fallbackUpdateInventory(UUID productId, Inventory inventory,
            Throwable throwable) {
        log.error("Fallback triggered for updateInventory due to: {}", throwable.getMessage());
        return Mono.error(new RuntimeException(
                "Inventory update is currently unavailable. Please try again later."));
    }

    public Mono<InventoryResponse> fallbackReserveInventory(UUID productId, Integer quantity,
            Throwable throwable) {
        log.error("Fallback triggered for reserveInventory due to: {}", throwable.getMessage());
        return Mono.error(new RuntimeException(
                "Inventory reservation is currently unavailable. Please try again later."));
    }

    public Mono<InventoryResponse> fallbackReleaseInventory(UUID productId, Integer quantity,
            Throwable throwable) {
        log.error("Fallback triggered for releaseInventory due to: {}", throwable.getMessage());
        return Mono.error(new RuntimeException(
                "Inventory release is currently unavailable. Please try again later."));
    }
}
