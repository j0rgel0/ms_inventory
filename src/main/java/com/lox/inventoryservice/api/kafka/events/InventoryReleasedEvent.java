package com.lox.inventoryservice.api.kafka.events;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    public Instant getTimestamp() {
        return timestamp;
    }

    // Static method to build the event from an Inventory
    public static InventoryReleasedEvent fromInventory(Inventory inventory) {
        return InventoryReleasedEvent.builder()
                .eventType(EventType.INVENTORY_RELEASED_NOTIFICATION.name())
                .inventoryId(inventory.getInventoryId())
                .productId(inventory.getProductId())
                .releasedQuantity(inventory.getReservedQuantity())
                .timestamp(Instant.now())
                .build();
    }

}
