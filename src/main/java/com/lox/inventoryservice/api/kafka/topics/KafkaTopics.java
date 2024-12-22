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

    public static final String INVENTORY_EVENTS_TOPIC = "inventory.events";
    public static final String INVENTORY_RELEASE_EVENTS_TOPIC = "inventory.release.events";
    public static final String ORDER_EVENTS_TOPIC = "order.events";

    private final KafkaConfig kafkaConfig;

    @Bean
    public NewTopic inventoryEventsTopic() {
        return new NewTopic("inventory.events", 3, (short) 1);
    }

    @Bean
    public NewTopic inventoryReleaseEventsTopic() {
        return new NewTopic("inventory.release.events", 3, (short) 1);
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return new NewTopic("order.events", 3, (short) 1);
    }
}
