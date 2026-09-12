package com.marketpulse.exceptions;

/**
 * Unchecked exception thrown when an operation is attempted on an order that no longer exists
 * or has already reached a terminal state (FILLED or CANCELLED).
 */
public class StaleOrderException extends RuntimeException {
    public StaleOrderException(String message) {
        super(message);
    }

    public StaleOrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
