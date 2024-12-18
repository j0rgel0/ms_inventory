package com.lox.inventoryservice.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.support.serializer.JsonSerializer;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public SenderOptions<String, Object> senderOptions(ObjectMapper objectMapper) {
        Map<String, Object> props = new HashMap<>();

        // Basic Producer Configs
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Idempotence Configuration
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

        // Additional Producer Configs (Optional)
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5); // Adjust as needed
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // Default batch size

        return SenderOptions.create(props);
    }

    @Bean
    public KafkaSender<String, Object> kafkaSender(SenderOptions<String, Object> senderOptions) {
        return KafkaSender.create(senderOptions);
    }

    /**
     * Generic method to create topics dynamically.
     *
     * @param name       The name of the topic.
     * @param partitions Number of partitions.
     * @param replicas   Number of replicas.
     * @return NewTopic instance.
     */
    public NewTopic createTopic(String name, int partitions, short replicas) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicas)
                .config("cleanup.policy", "delete")
                .build();
    }
}
