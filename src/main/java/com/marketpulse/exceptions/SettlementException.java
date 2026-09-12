package com.marketpulse.exceptions;

/**
 * Thrown when transactional settlement fails during trade execution,
 * indicating that the database transaction was rolled back and in-memory
 * state mutations were prevented.
 */
public class SettlementException extends RuntimeException {
    public SettlementException(String message) {
        super(message);
    }

    public SettlementException(String message, Throwable cause) {
        super(message, cause);
    }
}
