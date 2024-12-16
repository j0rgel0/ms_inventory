package com.lox.inventoryservice.kafka;

import lombok.Getter;
import lombok.experimental.UtilityClass;

@Getter
@UtilityClass
public final class KafkaTopics {

    // Topics for inter-service communication
    public static final String INVENTORY_CREATED_TOPIC = "inventory-created";
    public static final String INVENTORY_RESERVED_TOPIC = "inventory-reserved";
    public static final String INVENTORY_RELEASED_TOPIC = "inventory-released";
    public static final String INVENTORY_CONFIRMED_TOPIC = "inventory-confirmed";

    // Internal topics for other purposes if needed
    public static final String INVENTORY_EVENTS_TOPIC = "inventory-events";
    public static final String PRODUCT_UPDATED_TOPIC = "product-updated";

}
