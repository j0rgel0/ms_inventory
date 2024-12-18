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
public class InventoryReleasedEvent implements Event {

    private String eventType;
    private UUID inventoryId;
    private UUID productId;
    private Integer releasedQuantity;
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
    public static InventoryReleasedEvent fromInventory(Inventory inventory) {
        return InventoryReleasedEvent.builder()
                .eventType(EventType.INVENTORY_RELEASED.name())
                .inventoryId(inventory.getInventoryId())
                .productId(inventory.getProductId())
                .releasedQuantity(inventory.getReservedQuantity())
                .timestamp(Instant.now())
                .build();
    }

    // Overloaded method to create event with only productId
    public static InventoryReleasedEvent fromProductId(UUID productId) {
        return InventoryReleasedEvent.builder()
                .eventType(EventType.INVENTORY_RELEASED.name())
                .productId(productId)
                .timestamp(Instant.now())
                .build();
    }
}
