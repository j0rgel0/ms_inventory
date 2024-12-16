package com.lox.inventoryservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lox.inventoryservice.models.events.InventoryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class Producer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publishes an inventory event to the specified Kafka topic asynchronously.
     *
     * @param eventType The event type determining the topic.
     * @param event     The inventory event to publish.
     * @param <T>       The type of event extending InventoryEvent.
     * @return CompletableFuture<Void> indicating the completion of the send operation.
     */
    public <T extends InventoryEvent> CompletableFuture<Void> publishEvent(EventType eventType, T event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                    eventType.getTopic(), eventJson);

            return future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to publish event to {}: {}", eventType.getTopic(), throwable.getMessage());
                } else {
                    log.info("Successfully published event to {}: {} with offset [{}]",
                            eventType.getTopic(), eventJson, result.getRecordMetadata().offset());
                }
            }).thenApply(result -> null);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", e.getMessage());
            CompletableFuture<Void> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }
}
