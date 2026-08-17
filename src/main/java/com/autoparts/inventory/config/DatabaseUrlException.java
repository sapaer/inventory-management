package com.autoparts.inventory.config;

public class DatabaseUrlException extends IllegalStateException {
    public DatabaseUrlException(String message) {
        super(message);
    }

    public DatabaseUrlException(String message, Throwable cause) {
        super(message, cause);
    }
}
