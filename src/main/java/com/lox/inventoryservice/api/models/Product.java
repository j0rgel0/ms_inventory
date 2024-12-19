package com.lox.inventoryservice.api.models;

import java.math.BigDecimal;
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
public class Product {

    private UUID productId;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private Integer availableQuantity;
    private Instant createdAt;
    private Instant updatedAt;
}
