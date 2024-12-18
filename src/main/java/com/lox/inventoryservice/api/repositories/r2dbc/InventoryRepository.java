package com.lox.inventoryservice.api.repositories.r2dbc;

import com.lox.inventoryservice.api.models.Inventory;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface InventoryRepository extends ReactiveCrudRepository<Inventory, UUID>,
        InventoryRepositoryCustom {

    Mono<Inventory> findByProductId(UUID productId);

    Mono<Void> deleteByProductId(UUID productId);
}
