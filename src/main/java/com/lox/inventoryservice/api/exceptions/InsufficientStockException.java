// src/main/java/com/lox/inventoryservice/api/exceptions/InsufficientStockException.java

package com.lox.inventoryservice.api.exceptions;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
