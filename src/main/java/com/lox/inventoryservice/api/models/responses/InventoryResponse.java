package com.lox.inventoryservice.api.models.responses;

import com.lox.inventoryservice.api.models.Inventory;
import com.lox.inventoryservice.api.models.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {

    private Inventory inventory;
    private Product product;
}
