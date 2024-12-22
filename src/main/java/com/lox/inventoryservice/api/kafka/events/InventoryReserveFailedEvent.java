// src/main/java/com/lox/inventoryservice/api/kafka/events/InventoryReserveFailedEvent.java

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
 * An event indicating that the inventory reserve operation failed for an order.
 * Instead of a single reason string, we store a list of ReasonDetail to produce structured JSON.
 * Now includes trackId for tracing or correlation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReserveFailedEvent implements Event {

    private String eventType;
    private UUID trackId;
    private UUID orderId;
    private List<ReasonDetail> reasons;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return eventType;
    }

    /**
     * Use orderId to avoid null in the Kafka producer key.
     * Fallback to a zero-UUID if orderId is null.
     */
    @Override
    public UUID getProductId() {
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
