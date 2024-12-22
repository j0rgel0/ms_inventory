package com.lox.inventoryservice.api.kafka.events;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemEvent {
    private UUID productId;
    private Integer quantity;
}
