package com.marketpulse.exceptions;

/**
 * Checked exception thrown when a trader attempts to submit an order or withdraw
 * an amount that exceeds their cash balance.
 */
public class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String message) {
        super(message);
    }

    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
    }
}
