package com.lox.inventoryservice.api.models.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ReasonDetail {

    private UUID productId;
    private String productName;
    private String message;
}
