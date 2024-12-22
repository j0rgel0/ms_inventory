package com.lox.inventoryservice.api.models;

import java.util.UUID;
import lombok.Data;

@Data
public class OrderProduct {

    private UUID productId;
    private Integer quantity;
}
