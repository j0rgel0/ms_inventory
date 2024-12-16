package com.lox.inventoryservice.models.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseConfirmationResponse {
    private UUID orderId;
    private UUID productId;
    private String productName;
    private Integer quantityConfirmed;
    private String status;
}
