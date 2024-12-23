package com.lox.inventoryservice.api.models.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * DTO representing the command to reserve inventory for an order.
 */
@Data
@Builder
public class ReserveInventoryCommand {

    /**
     * Type of the event. Typically set to "RESERVE_INVENTORY_COMMAND".
     */
    @JsonProperty("eventType")
    private String eventType;

    /**
     * Unique identifier to track the flow of this command across services.
     */
    @JsonProperty("trackId")
    private UUID trackId;

    /**
     * Unique identifier of the order for which inventory needs to be reserved.
     */
    @JsonProperty("orderId")
    private UUID orderId;

    /**
     * List of items to reserve in the inventory.
     */
    @JsonProperty("items")
    private List<ReservedItemEvent> items;

    /**
     * Timestamp indicating when the command was created.
     */
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;
}
