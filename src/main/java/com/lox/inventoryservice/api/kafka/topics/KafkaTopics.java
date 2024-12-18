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

    private final KafkaConfig kafkaConfig;

    @Bean
    public NewTopic inventoryEventsTopic() {
        return kafkaConfig.createTopic(INVENTORY_EVENTS_TOPIC, 0, (short) 1);
    }
}
