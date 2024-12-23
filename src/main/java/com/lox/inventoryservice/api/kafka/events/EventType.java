package com.lox.inventoryservice.api.kafka.events;

import com.lox.inventoryservice.api.kafka.topics.KafkaTopics;
import lombok.Getter;

@Getter
public enum EventType {

    // ---- NOTIFICATIONS (end-user or external systems) ----
    INVENTORY_ADDED(KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC),
    INVENTORY_UPDATED(KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC),
    INVENTORY_REMOVED(KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC),

    // When inventory is successfully reserved (notification)
    INVENTORY_RESERVED_NOTIFICATION(KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC),

    // When inventory is successfully released (notification)
    INVENTORY_RELEASED_NOTIFICATION(KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC),

    // When inventory reservation fails (notification)
    INVENTORY_RESERVE_FAILED_NOTIFICATION(KafkaTopics.NOTIFICATIONS_EVENTS_TOPIC),


    // ---- STATUS / ORDER-LEVEL updates ----
    INVENTORY_RESERVED_STATUS(KafkaTopics.INVENTORY_STATUS_EVENTS_TOPIC),
    INVENTORY_RELEASED_STATUS(KafkaTopics.INVENTORY_STATUS_EVENTS_TOPIC),
    INVENTORY_RESERVE_FAILED_STATUS(KafkaTopics.INVENTORY_STATUS_EVENTS_TOPIC),

    // ---- COMMANDS (optional) ----
    RESERVE_INVENTORY_COMMAND("inventory.commands");
    // If you actually have a separate commands topic

    private final String topic;

    EventType(String topic) {
        this.topic = topic;
    }
}
