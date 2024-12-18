package com.lox.inventoryservice.api.services;

import com.lox.inventoryservice.api.models.Inventory;
import com.lox.inventoryservice.api.models.responses.InventoryResponse;
import com.lox.inventoryservice.api.models.page.InventoryPage;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    Mono<InventoryResponse> createInventory(Inventory inventory);

    Mono<Inventory> getInventoryByProductId(UUID productId);

    Mono<Inventory> updateInventory(UUID productId, Inventory inventory);

    Mono<Void> deleteInventory(UUID productId);

    Mono<InventoryPage> listInventory(List<String> filters, Pageable pageable);

    Mono<Void> reserveInventory(UUID productId, Integer quantity);

    Mono<Void> releaseInventory(UUID productId, Integer quantity);
}
