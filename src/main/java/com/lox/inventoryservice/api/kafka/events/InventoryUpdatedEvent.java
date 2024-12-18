package com.lox.inventoryservice.api.kafka.events;

import com.lox.inventoryservice.api.models.Inventory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUpdatedEvent implements Event {

    private String eventType;
    private UUID inventoryId;
    private UUID productId;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer reorderLevel;
    private Integer reorderQuantity;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return eventType;
    }

    @Override
    public UUID getProductId() {
        return productId;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    // Static method to build the event from an Inventory
    public static InventoryUpdatedEvent fromInventory(Inventory inventory) {
        return InventoryUpdatedEvent.builder()
                .eventType(EventType.INVENTORY_UPDATED.name())
                .inventoryId(inventory.getInventoryId())
                .productId(inventory.getProductId())
                .availableQuantity(inventory.getAvailableQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .reorderLevel(inventory.getReorderLevel())
                .reorderQuantity(inventory.getReorderQuantity())
                .timestamp(Instant.now())
                .build();
    }
}
