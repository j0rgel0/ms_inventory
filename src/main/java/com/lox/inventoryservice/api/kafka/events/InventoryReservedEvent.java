// src/main/java/com/lox/inventoryservice/api/kafka/events/InventoryReservedEvent.java

package com.lox.inventoryservice.api.kafka.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An event indicating the inventory was successfully reserved for an order.
 * Includes a list of reserved items (with prices), the total for the entire order,
 * and trackId for correlation/logging/tracing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservedEvent implements Event {

    private String eventType;
    private UUID trackId;
    private UUID orderId;
    private List<ReservedItemEvent> items;
    private Double orderTotal;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return eventType;
    }

    @Override
    public UUID getProductId() {
        // Use orderId if present to avoid NullPointerException in the producer’s key
        return (orderId != null)
                ? orderId
                : UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    @Override
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            timezone = "UTC"
    )
    public Instant getTimestamp() {
        return timestamp;
    }
}
