package com.lox.inventoryservice.models.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryReleasedEvent implements InventoryEvent {
    private UUID productId;
    private String productName;
    private Integer quantityReleased;
    private UUID orderId;
}
