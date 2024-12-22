package com.lox.inventoryservice.api.kafka.events;

import com.lox.inventoryservice.api.kafka.topics.KafkaTopics;
import lombok.Getter;

@Getter
public enum EventType {

    INVENTORY_ADDED(KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC),
    INVENTORY_UPDATED(KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC),
    INVENTORY_REMOVED(KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC),
    INVENTORY_RESERVED(KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC),
    INVENTORY_RELEASED(KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC),
    INVENTORY_RESERVE_FAILED(KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC),

    // (1)
    INVENTORY_RELEASED_ORDERS(KafkaTopics.INVENTORY_STATUS_EVENTS_TOPIC),
    INVENTORY_RESERVE_FAILED_ORDERS(KafkaTopics.INVENTORY_STATUS_EVENTS_TOPIC);

    private final String topic;

    EventType(String topic) {
        this.topic = topic;
    }
}
