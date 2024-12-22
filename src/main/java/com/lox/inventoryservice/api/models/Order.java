// src/main/java/com/lox/inventoryservice/api/models/Order.java

package com.lox.inventoryservice.api.models;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class Order {

    private UUID orderId;
    private UUID userId;
    private List<OrderProduct> products;
    private Double totalAmount;
    private String currency;
    private String status;
    private Instant timestamp;
}
