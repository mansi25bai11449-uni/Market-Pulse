package com.marketpulse.exceptions;

/**
 * Checked exception thrown when an order fails validation (e.g. non-positive quantity,
 * negative price for limit orders, missing symbol).
 */
public class InvalidOrderException extends Exception {
    public InvalidOrderException(String message) {
        super(message);
    }

    public InvalidOrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
