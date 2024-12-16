package com.lox.inventoryservice.repository;

import com.lox.inventoryservice.models.Inventory;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InventoryRepository extends ReactiveCrudRepository<Inventory, UUID> {
}
