package com.lox.inventoryservice.controllers;

import com.lox.inventoryservice.mapper.ProductMapper;
import com.lox.inventoryservice.models.Product;
import com.lox.inventoryservice.models.events.InventoryReservedEvent;
import com.lox.inventoryservice.models.requests.ConfirmPurchaseRequest;
import com.lox.inventoryservice.models.requests.ReserveInventoryRequest;
import com.lox.inventoryservice.models.responses.InventoryResponse;
import com.lox.inventoryservice.models.responses.PurchaseConfirmationResponse;
import com.lox.inventoryservice.services.ProductService;
import com.lox.inventoryservice.kafka.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Validated
public class InventoryController {

    private final ProductService productService;
    private final ProductMapper productMapper;
    private final com.lox.inventoryservice.kafka.Producer producer;

    // Existing CRUD endpoints...

    /**
     * Endpoint to reserve inventory for an order.
     *
     * @param request ReserveInventoryRequest containing productId, quantity, and orderId.
     * @return Mono<InventoryResponse> Response containing updated inventory details.
     */
    @PostMapping("/reserve")
    @ResponseStatus(HttpStatus.OK)
    public Mono<InventoryResponse> reserveInventory(@Valid @RequestBody ReserveInventoryRequest request) {
        return productService.reserveQuantity(request.getProductId(), request.getQuantity())
                .flatMap(product -> {
                    // Publish InventoryReservedEvent
                    InventoryReservedEvent event = new InventoryReservedEvent(
                            product.getId(),
                            product.getName(),
                            request.getQuantity(),
                            request.getOrderId()
                    );
                    return Mono.fromFuture(producer.publishEvent(EventType.INVENTORY_RESERVED, event))
                            .thenReturn(productMapper.toInventoryResponse(product));
                });
    }

    /**
     * Endpoint to confirm a purchase, finalizing the inventory reduction.
     *
     * @param request ConfirmPurchaseRequest containing productId, quantity, and orderId.
     * @return Mono<PurchaseConfirmationResponse> Response confirming the purchase.
     */
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.OK)
    public Mono<PurchaseConfirmationResponse> confirmPurchase(@Valid @RequestBody ConfirmPurchaseRequest request) {
        return productService.confirmPurchase(request.getProductId(), request.getQuantity())
                .flatMap(product -> {
                    // Publish InventoryConfirmedEvent
                    com.lox.inventoryservice.models.events.InventoryConfirmedEvent event =
                            new com.lox.inventoryservice.models.events.InventoryConfirmedEvent(
                                    product.getId(),
                                    product.getName(),
                                    request.getQuantity(),
                                    request.getOrderId()
                            );
                    return Mono.fromFuture(producer.publishEvent(EventType.INVENTORY_CONFIRMED, event))
                            .thenReturn(new PurchaseConfirmationResponse(
                                    request.getOrderId(),
                                    product.getId(),
                                    product.getName(),
                                    request.getQuantity(),
                                    "Confirmed"
                            ));
                });
    }

    /**
     * Endpoint to release reserved inventory, typically used when an order is cancelled.
     *
     * @param request ReserveInventoryRequest containing productId, quantity, and orderId.
     * @return Mono<InventoryResponse> Response containing updated inventory details.
     */
    @PostMapping("/release")
    @ResponseStatus(HttpStatus.OK)
    public Mono<InventoryResponse> releaseReservedInventory(@Valid @RequestBody ReserveInventoryRequest request) {
        return productService.releaseReservedQuantity(request.getProductId(), request.getQuantity())
                .flatMap(product -> {
                    // Publish InventoryReleasedEvent
                    com.lox.inventoryservice.models.events.InventoryReleasedEvent event =
                            new com.lox.inventoryservice.models.events.InventoryReleasedEvent(
                                    product.getId(),
                                    product.getName(),
                                    request.getQuantity(),
                                    request.getOrderId()
                            );
                    return Mono.fromFuture(producer.publishEvent(EventType.INVENTORY_RELEASED, event))
                            .thenReturn(productMapper.toInventoryResponse(product));
                });
    }
}
