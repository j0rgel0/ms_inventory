package com.lox.inventoryservice.api.kafka.events;

import java.util.UUID;

public interface ProductEvent extends Event {
    UUID getProductId();
}
