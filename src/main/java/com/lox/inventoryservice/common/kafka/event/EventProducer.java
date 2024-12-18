package com.lox.inventoryservice.common.kafka.event;

import com.lox.inventoryservice.api.kafka.events.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventProducer {

    private final KafkaSender<String, Object> kafkaSender;

    /**
     * Publishes an event to the specified topic reactively.
     *
     * @param topic The topic to publish the event to.
     * @param event The event to publish.
     * @param <T>   The type of event implementing the Event interface.
     * @return Mono<Void> Indicates the completion of the send operation.
     */
    public <T extends Event> Mono<Void> publishEvent(String topic, Event event) {
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(
                new org.apache.kafka.clients.producer.ProducerRecord<>(topic, event.getProductId().toString(), event),
                event.getEventType() // Correlation metadata, can be any unique identifier
        );

        return kafkaSender.send(Mono.just(senderRecord))
                .doOnNext(result -> log.info("Successfully published event to {}: {} with offset [{}]",
                        topic, event, result.recordMetadata().offset()))
                .doOnError(error -> log.error("Failed to publish event to {}: {}", topic, error.getMessage()))
                .then(); // Convert to Mono<Void>
    }
}