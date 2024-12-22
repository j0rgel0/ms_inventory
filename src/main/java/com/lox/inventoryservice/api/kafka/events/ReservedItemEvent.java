package com.lox.inventoryservice.api.kafka.events;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single reserved product in the successful InventoryReservedEvent,
 * including quantity, unit price, and total price for that line item.
 */
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
