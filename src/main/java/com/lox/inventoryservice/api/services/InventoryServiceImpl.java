// src/main/java/com/lox/inventoryservice/api/services/InventoryServiceImpl.java

package com.lox.inventoryservice.api.services;

import com.lox.inventoryservice.api.exceptions.InventoryNotFoundException;
import com.lox.inventoryservice.api.kafka.events.EventType;
import com.lox.inventoryservice.api.kafka.events.InventoryAddedEvent;
import com.lox.inventoryservice.api.kafka.events.InventoryReleasedEvent;
import com.lox.inventoryservice.api.kafka.events.InventoryRemovedEvent;
import com.lox.inventoryservice.api.kafka.events.InventoryReservedEvent;
import com.lox.inventoryservice.api.kafka.events.InventoryUpdatedEvent;
import com.lox.inventoryservice.api.models.Inventory;
import com.lox.inventoryservice.api.models.Product;
import com.lox.inventoryservice.api.models.page.InventoryPage;
import com.lox.inventoryservice.api.models.responses.InventoryResponse;
import com.lox.inventoryservice.api.repositories.r2dbc.InventoryRepository;
import com.lox.inventoryservice.common.kafka.event.EventProducer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
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

    @Override
    @Transactional
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackCreateInventory")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackCreateInventory")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackCreateInventory")
    public Mono<InventoryResponse> createInventory(Inventory inventory) {
        // Validation: availableQuantity must be greater than 0
        if (inventory.getAvailableQuantity() == null || inventory.getAvailableQuantity() < 1) {
            return Mono.error(
                    new IllegalArgumentException("Available quantity must be greater than 0."));
        }

        UUID productId = inventory.getProductId();
        if (productId == null) {
            return Mono.error(new IllegalArgumentException("Product ID must be provided."));
        }

        log.info("Creating inventory for Product ID: {}", productId);

        // Verify if the product exists by calling the ProductCatalogService
        return productCatalogWebClient.get()
                .uri("/api/products/{productId}", productId)
                .retrieve()
                .bodyToMono(Product.class)
                .flatMap(product -> {
                    // Log info indicating that the product was found and its details
                    log.info("Product found: {}", product);

                    // Product exists, proceed to create inventory
                    inventory.setInventoryId(UUID.randomUUID());
                    inventory.setReservedQuantity(0);
                    inventory.setLastUpdated(Instant.now());

                    return r2dbcEntityTemplate.insert(
                                    Inventory.class) // Changed from inventoryRepository.save
                            .using(inventory)
                            .flatMap(savedInventory ->
                                    eventProducer.publishEvent(EventType.INVENTORY_ADDED.getTopic(),
                                                    InventoryAddedEvent.fromInventory(savedInventory))
                                            .thenReturn(savedInventory)
                            )
                            .flatMap(savedInventory ->
                                    hashOps.put(HASH_KEY, savedInventory.getProductId().toString(),
                                                    savedInventory)
                                            .thenReturn(savedInventory)
                            )
                            .flatMap(savedInventory -> fetchProductAndBuildResponse(savedInventory))
                            .doOnSuccess(invResponse -> log.info(
                                    "Inventory created and cached for Product ID: {}",
                                    invResponse.getInventory().getProductId()))
                            .doOnError(
                                    e -> log.error("Error creating inventory: {}", e.getMessage()));
                })
                .onErrorResume(e -> {
                    log.error("Product not found or error occurred: {}", e.getMessage());
                    return Mono.error(
                            new RuntimeException("Product does not exist or an error occurred."));
                });

    }

    @Override
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackGetInventoryByProductId")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackGetInventoryByProductId")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackGetInventoryByProductId")
    public Mono<InventoryResponse> getInventoryByProductId(UUID productId) {
        log.info("Fetching inventory for Product ID: {}", productId);
        String key = productId.toString();

        return hashOps.get(HASH_KEY, key)
                .flatMap(cachedInventory -> {
                    if (cachedInventory != null) {
                        log.info("Inventory retrieved from cache for Product ID: {}", productId);
                        return fetchProductAndBuildResponse(cachedInventory);
                    } else {
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(
                        inventoryRepository.findByProductId(productId)
                                .switchIfEmpty(Mono.error(new InventoryNotFoundException(
                                        "Inventory not found for Product ID: " + productId)))
                                .flatMap(inventory ->
                                        hashOps.put(HASH_KEY, inventory.getProductId().toString(),
                                                        inventory)
                                                .thenReturn(inventory)
                                )
                                .flatMap(this::fetchProductAndBuildResponse)
                )
                .doOnNext(invResponse -> log.info("Inventory retrieved for Product ID: {}",
                        invResponse.getInventory().getProductId()));
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackUpdateInventory")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackUpdateInventory")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackUpdateInventory")
    public Mono<InventoryResponse> updateInventory(UUID productId, Inventory inventory) {
        log.info("Updating inventory for Product ID: {}", productId);

        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new InventoryNotFoundException(
                        "Inventory not found for Product ID: " + productId)))
                .flatMap(existingInventory -> {
                    existingInventory.setAvailableQuantity(inventory.getAvailableQuantity());
                    existingInventory.setReorderLevel(inventory.getReorderLevel());
                    existingInventory.setReorderQuantity(inventory.getReorderQuantity());
                    existingInventory.setLastUpdated(Instant.now());

                    return r2dbcEntityTemplate.update(Inventory.class)
                            .matching(Query.query(Criteria.where("product_id").is(productId)))
                            .apply(Update.update("available_quantity",
                                            existingInventory.getAvailableQuantity())
                                    .set("reorder_level", existingInventory.getReorderLevel())
                                    .set("reorder_quantity", existingInventory.getReorderQuantity())
                                    .set("last_updated", existingInventory.getLastUpdated()))
                            .thenReturn(existingInventory);
                })
                .flatMap(updatedInventory ->
                        eventProducer.publishEvent(EventType.INVENTORY_UPDATED.getTopic(),
                                        InventoryUpdatedEvent.fromInventory(updatedInventory))
                                .thenReturn(updatedInventory)
                )
                .flatMap(updatedInventory ->
                        hashOps.put(HASH_KEY, updatedInventory.getProductId().toString(),
                                        updatedInventory)
                                .thenReturn(updatedInventory)
                )
                .flatMap(this::fetchProductAndBuildResponse)
                .doOnSuccess(invResponse -> log.info(
                        "Inventory updated and cache refreshed for Product ID: {}",
                        invResponse.getInventory().getProductId()))
                .doOnError(e -> log.error("Error updating inventory: {}", e.getMessage()));
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackDeleteInventory")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackDeleteInventory")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackDeleteInventory")
    public Mono<Void> deleteInventory(UUID productId) {
        log.info("Deleting inventory for Product ID: {}", productId);
        String key = productId.toString();

        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new InventoryNotFoundException(
                        "Inventory not found for Product ID: " + productId)))
                .flatMap(existingInventory ->
                        r2dbcEntityTemplate.delete(Inventory.class)
                                .matching(Query.query(Criteria.where("product_id").is(productId)))
                                .all()
                                .then(eventProducer.publishEvent(
                                        EventType.INVENTORY_REMOVED.getTopic(),
                                        InventoryRemovedEvent.fromProductId(productId)))
                                .then(hashOps.remove(HASH_KEY, key))
                )
                .then()
                .doOnSuccess(v -> log.info("Inventory deleted and cache removed for Product ID: {}",
                        productId))
                .doOnError(e -> log.error("Error deleting inventory: {}", e.getMessage()));
    }

    @Override
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackListInventory")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackListInventory")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackListInventory")
    public Mono<InventoryPage> listInventory(List<String> filters, Pageable pageable) {
        log.info("Listing inventory with filters: {} and pageable: {}", filters, pageable);

        // Example filter: reorderNeeded=true
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

        Mono<Long> totalElementsMono = reorderNeeded != null
                ? inventoryRepository.findAllByReorderNeeded(reorderNeeded).count()
                : inventoryRepository.count();

        Mono<List<Inventory>> inventoryMono = reorderNeeded != null
                ? inventoryRepository.findAllByReorderNeeded(reorderNeeded)
                .skip(pageable.getOffset())
                .take(pageable.getPageSize()).collectList()
                : inventoryRepository.findAll().skip(pageable.getOffset())
                        .take(pageable.getPageSize()).collectList();

        return Mono.zip(totalElementsMono, inventoryMono)
                .map(tuple -> {
                    long totalElements = tuple.getT1();
                    List<Inventory> inventories = tuple.getT2();
                    int totalPages = (int) Math.ceil(
                            (double) totalElements / pageable.getPageSize());
                    int currentPage = pageable.getPageNumber();
                    int pageSize = pageable.getPageSize();

                    return new InventoryPage(inventories, totalElements, totalPages, currentPage,
                            pageSize);
                })
                .doOnSuccess(page -> log.info("Retrieved {} inventories out of {}",
                        page.getInventories().size(), page.getTotalElements()))
                .doOnError(e -> log.error("Error retrieving inventory page: {}", e.getMessage()));
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackReserveInventory")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackReserveInventory")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackReserveInventory")
    public Mono<InventoryResponse> reserveInventory(UUID productId, Integer quantity) {
        log.info("Reserving {} units for Product ID: {}", quantity, productId);

        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new InventoryNotFoundException(
                        "Inventory not found for Product ID: " + productId)))
                .flatMap(inventory -> {
                    if (inventory.getAvailableQuantity() < quantity) {
                        log.warn("Insufficient stock for Product ID: {}", productId);
                        return Mono.error(new RuntimeException("Insufficient stock available."));
                    }

                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
                    inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
                    inventory.setLastUpdated(Instant.now());

                    return r2dbcEntityTemplate.update(Inventory.class)
                            .matching(Query.query(Criteria.where("product_id").is(productId)))
                            .apply(Update.update("available_quantity",
                                            inventory.getAvailableQuantity())
                                    .set("reserved_quantity", inventory.getReservedQuantity())
                                    .set("last_updated", inventory.getLastUpdated()))
                            .thenReturn(inventory);
                })
                .flatMap(updatedInventory ->
                        eventProducer.publishEvent(EventType.INVENTORY_RESERVED.getTopic(),
                                        InventoryReservedEvent.fromInventory(updatedInventory))
                                .thenReturn(updatedInventory)
                )
                .flatMap(updatedInventory ->
                        hashOps.put(HASH_KEY, updatedInventory.getProductId().toString(),
                                        updatedInventory)
                                .thenReturn(updatedInventory)
                )
                .flatMap(this::fetchProductAndBuildResponse)
                .doOnSuccess(invResponse -> log.info(
                        "Inventory reserved and cache refreshed for Product ID: {}",
                        invResponse.getInventory().getProductId()))
                .doOnError(e -> log.error("Error reserving inventory: {}", e.getMessage()));
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "inventoryServiceCB", fallbackMethod = "fallbackReleaseInventory")
    @Retry(name = "inventoryServiceRetry", fallbackMethod = "fallbackReleaseInventory")
    @RateLimiter(name = "inventoryServiceRateLimiter", fallbackMethod = "fallbackReleaseInventory")
    public Mono<InventoryResponse> releaseInventory(UUID productId, Integer quantity) {
        log.info("Releasing {} units for Product ID: {}", quantity, productId);

        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new InventoryNotFoundException(
                        "Inventory not found for Product ID: " + productId)))
                .flatMap(inventory -> {
                    if (inventory.getReservedQuantity() < quantity) {
                        log.warn("Cannot release more than reserved for Product ID: {}", productId);
                        return Mono.error(new RuntimeException(
                                "Cannot release more than reserved quantity."));
                    }

                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
                    inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
                    inventory.setLastUpdated(Instant.now());

                    return r2dbcEntityTemplate.update(Inventory.class)
                            .matching(Query.query(Criteria.where("product_id").is(productId)))
                            .apply(Update.update("available_quantity",
                                            inventory.getAvailableQuantity())
                                    .set("reserved_quantity", inventory.getReservedQuantity())
                                    .set("last_updated", inventory.getLastUpdated()))
                            .thenReturn(inventory);
                })
                .flatMap(updatedInventory ->
                        eventProducer.publishEvent(EventType.INVENTORY_RELEASED.getTopic(),
                                        InventoryReleasedEvent.fromInventory(updatedInventory))
                                .thenReturn(updatedInventory)
                )
                .flatMap(updatedInventory ->
                        hashOps.put(HASH_KEY, updatedInventory.getProductId().toString(),
                                        updatedInventory)
                                .thenReturn(updatedInventory)
                )
                .flatMap(this::fetchProductAndBuildResponse)
                .doOnSuccess(invResponse -> log.info(
                        "Inventory released and cache refreshed for Product ID: {}",
                        invResponse.getInventory().getProductId()))
                .doOnError(e -> log.error("Error releasing inventory: {}", e.getMessage()));
    }

    // Helper method to fetch Product and build InventoryResponse
    private Mono<InventoryResponse> fetchProductAndBuildResponse(Inventory inventory) {
        UUID productId = inventory.getProductId();
        return productCatalogWebClient.get()
                .uri("/api/products/{productId}", productId)
                .retrieve()
                .bodyToMono(Product.class)
                .map(product -> InventoryResponse.builder()
                        .inventory(inventory)
                        .product(product)
                        .build())
                .doOnError(e -> log.error("Error fetching product details: {}", e.getMessage()));
    }

    // Fallback methods

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
