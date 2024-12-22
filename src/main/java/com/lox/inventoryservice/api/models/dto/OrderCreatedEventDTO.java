// src/main/java/com/lox/inventoryservice/api/kafka/events/OrderCreatedEventDTO.java

package com.lox.inventoryservice.api.models.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class OrderCreatedEventDTO {

    private String eventType;
    private UUID trackId;      // New field
    private UUID orderId;
    private UUID userId;
    private Double totalAmount;
    private String currency;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<OrderItemDTO> items; // Contains productId, quantity, etc.

}