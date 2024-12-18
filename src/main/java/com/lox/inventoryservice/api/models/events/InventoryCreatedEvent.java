package com.lox.inventoryservice.api.models.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryCreatedEvent implements InventoryEvent {
    private UUID productId;
    private String productName;
    private Integer quantity;
}
