package com.lox.inventoryservice.api.models.events;

public class InventoryReservedEvent {

}

//InventoryCreatedEvent: Notifies other services that a new product has been added.
//InventoryReservedEvent: Indicates that a certain quantity of a product has been reserved for an order.
//InventoryReleasedEvent: Indicates that previously reserved inventory has been released, typically due to order cancellation.
//InventoryConfirmedEvent: Confirms the finalization of an order, reducing the inventory accordingly.