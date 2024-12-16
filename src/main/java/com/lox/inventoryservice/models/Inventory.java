// src/main/java/com/lox/inventoryservice/models/Inventory.java

package com.lox.inventoryservice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("inventory")
public class Inventory {

    @Id
    private UUID id;

    private String productName;
    private String description;
    private Integer quantity;
    private BigDecimal price;
    private Instant createdAt;

    // Otros campos y métodos según sea necesario
}
