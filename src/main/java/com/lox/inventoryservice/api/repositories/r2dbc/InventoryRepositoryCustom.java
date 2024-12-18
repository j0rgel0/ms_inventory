package com.lox.inventoryservice.api.repositories.r2dbc;

import com.lox.inventoryservice.api.models.Inventory;
import reactor.core.publisher.Flux;

public interface InventoryRepositoryCustom {

    Flux<Inventory> findAllByReorderNeeded(Boolean reorderNeeded);
}
