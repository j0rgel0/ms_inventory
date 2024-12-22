package com.lox.inventoryservice.api.models.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservedItemEvent {
    private UUID productId;
    private Integer quantity;
    private Double unitPrice;   // Price for a single unit
    private Double totalPrice;  // unitPrice * quantity
}
