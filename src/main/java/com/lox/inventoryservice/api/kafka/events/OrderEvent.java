package com.lox.inventoryservice.api.kafka.events;

import java.util.UUID;

public interface OrderEvent extends Event {
    UUID getOrderId();
}
