package com.lox.inventoryservice.api.services;

import com.lox.inventoryservice.api.models.Inventory;
import com.lox.inventoryservice.api.models.page.InventoryPage;
import com.lox.inventoryservice.api.models.responses.InventoryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

public interface InventoryService {

    Mono<InventoryResponse> createInventory(Inventory inventory);

    Mono<InventoryResponse> updateInventory(UUID productId, Inventory inventory);

    Mono<InventoryResponse> reserveInventory(UUID productId, Integer quantity);

    Mono<InventoryResponse> releaseInventory(UUID productId, Integer quantity);

    Mono<InventoryResponse> getInventoryByProductId(UUID productId);

    Mono<Void> deleteInventory(UUID productId);

    Mono<InventoryPage> listInventory(List<String> filters, Pageable pageable);
}
