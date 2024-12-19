package com.lox.inventoryservice.api.models.responses;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
