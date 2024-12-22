// src/main/java/com/lox/inventoryservice/api/kafka/topics/KafkaTopics.java

package com.lox.inventoryservice.api.kafka.topics;

import com.lox.inventoryservice.common.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class KafkaTopics {

    public static final String INVENTORY_STATUS_EVENTS_TOPIC = "inventory.status.events";
    public static final String NOTIFICATIONS_EVENTS_TOPIC = "notification.events";

    private final KafkaConfig kafkaConfig;

    @Bean
    public NewTopic inventoryStatusEventsTopic() {
        return new NewTopic(INVENTORY_STATUS_EVENTS_TOPIC, 3, (short) 1);
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        return new NewTopic(NOTIFICATIONS_EVENTS_TOPIC, 3, (short) 1);
    }

}
