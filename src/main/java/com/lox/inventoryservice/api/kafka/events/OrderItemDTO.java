package com.lox.inventoryservice.api.kafka.events;

import java.util.UUID;
import lombok.Data;

@Data
public class OrderItemDTO {

    private UUID orderItemId;
    private UUID productId;
    private Integer quantity;
    private Double price;
}
