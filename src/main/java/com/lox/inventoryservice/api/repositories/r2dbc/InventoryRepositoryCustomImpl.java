package com.lox.inventoryservice.api.repositories.r2dbc;

import com.lox.inventoryservice.api.models.Inventory;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public class InventoryRepositoryCustomImpl implements InventoryRepositoryCustom {

    private final R2dbcEntityTemplate template;

    public InventoryRepositoryCustomImpl(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Flux<Inventory> findAllByReorderNeeded(Boolean reorderNeeded) {
        if (reorderNeeded == null) {
            return template.select(Inventory.class).all();
        }

        String sql;
        if (reorderNeeded) {
            sql = "SELECT * FROM inventory WHERE available_quantity <= reorder_level";
        } else {
            sql = "SELECT * FROM inventory WHERE available_quantity > reorder_level";
        }

        return template.getDatabaseClient()
                .sql(sql)
                .map((row, metadata) -> Inventory.builder()
                        .inventoryId(row.get("inventory_id", UUID.class))
                        .productId(row.get("product_id", UUID.class))
                        .availableQuantity(row.get("available_quantity", Integer.class))
                        .reservedQuantity(row.get("reserved_quantity", Integer.class))
                        .reorderLevel(row.get("reorder_level", Integer.class))
                        .reorderQuantity(row.get("reorder_quantity", Integer.class))
                        .lastUpdated(row.get("last_updated", Instant.class))
                        .build())
                .all();
    }
}
