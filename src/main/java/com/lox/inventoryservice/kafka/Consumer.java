package com.lox.inventoryservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lox.inventoryservice.models.events.InventoryConfirmedEvent;
import com.lox.inventoryservice.models.events.InventoryReleasedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class Consumer {

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    /**
     * Listens to INVENTORY_CONFIRMED_TOPIC to finalize inventory reduction.
     *
     * @param message The incoming message.
     */
    @KafkaListener(topics = KafkaTopics.INVENTORY_CONFIRMED_TOPIC, groupId = "inventory-service-group")
    public void handleInventoryConfirmed(String message) {
        try {
            InventoryConfirmedEvent event = objectMapper.readValue(message,
                    InventoryConfirmedEvent.class);
            log.info("Received InventoryConfirmedEvent: {}", event);
            productService.confirmPurchase(event.getProductId(), event.getQuantityConfirmed())
                    .subscribe(
                            product -> log.info("Purchase confirmed for product: {}",
                                    product.getName()),
                            error -> log.error("Error confirming purchase: {}", error.getMessage())
                    );
        } catch (Exception e) {
            log.error("Failed to process InventoryConfirmedEvent: {}", e.getMessage());
        }
    }

    /**
     * Listens to INVENTORY_RELEASED_TOPIC to release reserved inventory.
     *
     * @param message The incoming message.
     */
    @KafkaListener(topics = KafkaTopics.INVENTORY_RELEASED_TOPIC, groupId = "inventory-service-group")
    public void handleInventoryReleased(String message) {
        try {
            InventoryReleasedEvent event = objectMapper.readValue(message,
                    InventoryReleasedEvent.class);
            log.info("Received InventoryReleasedEvent: {}", event);
            productService.releaseReservedQuantity(event.getProductId(),
                            event.getQuantityReleased())
                    .subscribe(
                            product -> log.info("Released reserved inventory for product: {}",
                                    product.getName()),
                            error -> log.error("Error releasing inventory: {}", error.getMessage())
                    );
        } catch (Exception e) {
            log.error("Failed to process InventoryReleasedEvent: {}", e.getMessage());
        }
    }
}
