package com.lox.inventoryservice.kafka;

import lombok.Getter;

@Getter
public enum EventType {
    INVENTORY_CREATED(KafkaTopics.INVENTORY_CREATED_TOPIC),
    INVENTORY_RESERVED(KafkaTopics.INVENTORY_RESERVED_TOPIC),
    INVENTORY_RELEASED(KafkaTopics.INVENTORY_RELEASED_TOPIC),
    INVENTORY_CONFIRMED(KafkaTopics.INVENTORY_CONFIRMED_TOPIC),
    INVENTORY_UPDATED(KafkaTopics.PRODUCT_UPDATED_TOPIC);

    private final String topic;

    EventType(String topic) {
        this.topic = topic;
    }
}
