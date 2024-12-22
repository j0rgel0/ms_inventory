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
 * Instead of a single string 'reason', we store a list of ReasonDetail to
 * produce structured JSON output. Includes a static builder method for convenience.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReserveFailedEvent implements Event {

    private String eventType;
    private UUID orderId;
    private List<ReasonDetail> reasons;
    private Instant timestamp;

    /**
     * Returns the event type (e.g., "INVENTORY_RESERVE_FAILED").
     */
    @Override
    public String getEventType() {
        return eventType;
    }

    /**
     * Use orderId to avoid null productId in the publishEvent(...) method,
     * since the producer uses getProductId().toString() as the Kafka key.
     * Fallback to a zero-UUID if orderId is null.
     */
    @Override
    public UUID getProductId() {
        return (orderId != null)
                ? orderId
                : UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    /**
     * ISO 8601 UTC timestamp.
     */
    @Override
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            timezone = "UTC"
    )
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Convenience static builder to create a failed event with the current timestamp.
     */
    public static InventoryReserveFailedEvent fromOrder(
            UUID orderId,
            List<ReasonDetail> reasons
    ) {
        return InventoryReserveFailedEvent.builder()
                .eventType(EventType.INVENTORY_RESERVE_FAILED.name())
                .orderId(orderId)
                .reasons(reasons)
                .timestamp(Instant.now())
                .build();
    }
}
