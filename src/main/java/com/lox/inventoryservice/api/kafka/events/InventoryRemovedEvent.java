package com.lox.inventoryservice.api.kafka.events;

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
public class InventoryRemovedEvent implements Event {

    private String eventType;
    private UUID productId;
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

    // Static method to build the event from a productId
    public static InventoryRemovedEvent fromProductId(UUID productId) {
        return InventoryRemovedEvent.builder()
                .eventType(EventType.INVENTORY_REMOVED.name())
                .productId(productId)
                .timestamp(Instant.now())
                .build();
    }
}
