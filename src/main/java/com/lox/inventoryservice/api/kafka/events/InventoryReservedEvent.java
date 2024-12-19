package com.lox.inventoryservice.api.kafka.events;

import com.lox.inventoryservice.api.models.Inventory;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservedEvent implements Event {

    private String eventType;
    private UUID inventoryId;
    private UUID productId;
    private Integer reservedQuantity;
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
    public static InventoryReservedEvent fromInventory(Inventory inventory) {
        return InventoryReservedEvent.builder()
                .eventType(EventType.INVENTORY_RESERVED.name())
                .inventoryId(inventory.getInventoryId())
                .productId(inventory.getProductId())
                .reservedQuantity(inventory.getReservedQuantity())
                .timestamp(Instant.now())
                .build();
    }
}
