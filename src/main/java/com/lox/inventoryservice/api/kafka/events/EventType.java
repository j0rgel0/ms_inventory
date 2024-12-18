package com.lox.inventoryservice.api.kafka.events;

import com.lox.inventoryservice.api.kafka.topics.KafkaTopics;
import lombok.Getter;

@Getter
public enum EventType {
    INVENTORY_ADDED(KafkaTopics.INVENTORY_EVENTS_TOPIC),
    INVENTORY_UPDATED(KafkaTopics.INVENTORY_EVENTS_TOPIC),
    INVENTORY_REMOVED(KafkaTopics.INVENTORY_EVENTS_TOPIC),
    INVENTORY_RESERVED(KafkaTopics.INVENTORY_EVENTS_TOPIC),
    INVENTORY_RELEASED(KafkaTopics.INVENTORY_EVENTS_TOPIC);

    private final String topic;

    EventType(String topic) {
        this.topic = topic;
    }

}
