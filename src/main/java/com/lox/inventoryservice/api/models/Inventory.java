package com.lox.inventoryservice.api.models;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("inventory")
public class Inventory {

    @Id
    private UUID inventoryId;
    private UUID productId;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer reorderLevel;
    private Integer reorderQuantity;
    private Instant lastUpdated;
}
