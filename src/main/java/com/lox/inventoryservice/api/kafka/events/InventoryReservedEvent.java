// src/main/java/com/lox/inventoryservice/api/kafka/events/InventoryReservedEvent.java

package com.lox.inventoryservice.api.kafka.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lox.inventoryservice.api.models.Inventory;
import java.time.Instant;
import java.util.List;
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
    private UUID orderId;
    private List<OrderItemEvent> items;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return eventType;
    }

    @Override
    public UUID getProductId() {
        // If orderId is null, fallback to a static "zero" UUID or random UUID
        return (orderId != null)
                ? orderId
                : UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    public Instant getTimestamp() {
        return timestamp;
    }

    public static InventoryReservedEvent fromOrder(UUID orderId, List<OrderItemEvent> items) {
        return InventoryReservedEvent.builder()
                .eventType(EventType.INVENTORY_RESERVED.name())
                .orderId(orderId)
                .items(items)
                .timestamp(Instant.now())
                .build();
    }

    public static InventoryReservedEvent fromInventory(Inventory inventory) {
        OrderItemEvent itemEvent = new OrderItemEvent(inventory.getProductId(), inventory.getReservedQuantity());
        return InventoryReservedEvent.builder()
                .eventType(EventType.INVENTORY_RESERVED.name())
                .orderId(null) // just for testing
                .items(List.of(itemEvent))
                .timestamp(Instant.now())
                .build();
    }
}
