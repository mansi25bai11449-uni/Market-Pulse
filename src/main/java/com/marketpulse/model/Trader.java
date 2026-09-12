package com.marketpulse.model;

import com.marketpulse.exceptions.InsufficientFundsException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe Trader domain model tracking cash balance, reserved margin,
 * and per-symbol stock inventory.
 */
public class Trader {
    private final String traderId;
    private final String name;
    private final AtomicReference<Double> cashBalance;
    private final AtomicReference<Double> reservedCash;
    private final ConcurrentHashMap<String, Integer> stockHoldings;

    public Trader(String traderId, String name, double initialCash) {
        this.traderId = Objects.requireNonNull(traderId, "traderId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.cashBalance = new AtomicReference<>(initialCash);
        this.reservedCash = new AtomicReference<>(0.0);
        this.stockHoldings = new ConcurrentHashMap<>();
    }

    public String getTraderId() { return traderId; }
    public String getName() { return name; }
    public double getCashBalance() { return cashBalance.get(); }
    public double getReservedCash() { return reservedCash.get(); }
    public double getAvailableCash() { return Math.max(0.0, cashBalance.get() - reservedCash.get()); }

    public synchronized void deposit(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        cashBalance.updateAndGet(curr -> curr + amount);
    }

    public synchronized void withdraw(double amount) throws InsufficientFundsException {
        if (amount < 0) {
            throw new IllegalArgumentException("Withdraw amount must be positive");
        }
        if (cashBalance.get() < amount) {
            throw new InsufficientFundsException(String.format(
                    "Trader %s has insufficient funds: required=%.2f, available=%.2f",
                    traderId, amount, cashBalance.get()));
        }
        cashBalance.updateAndGet(curr -> curr - amount);
    }

    public synchronized void reserveCash(double amount) throws InsufficientFundsException {
        if (amount < 0) throw new IllegalArgumentException("Reservation amount must be positive");
        if (getAvailableCash() < amount) {
            throw new InsufficientFundsException(String.format(
                    "Trader %s cannot reserve $%.2f: available margin is $%.2f",
                    traderId, amount, getAvailableCash()));
        }
        reservedCash.updateAndGet(curr -> curr + amount);
    }

    public synchronized void releaseCash(double amount) {
        if (amount < 0) throw new IllegalArgumentException("Release amount must be positive");
        reservedCash.updateAndGet(curr -> Math.max(0.0, curr - amount));
    }

    public int getShares(String symbol) {
        return stockHoldings.getOrDefault(symbol.toUpperCase(), 0);
    }

    public void addShares(String symbol, int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Quantity must be positive");
        stockHoldings.merge(symbol.toUpperCase(), quantity, Integer::sum);
    }

    public synchronized void deductShares(String symbol, int quantity) throws InsufficientFundsException {
        if (quantity < 0) throw new IllegalArgumentException("Quantity must be positive");
        String sym = symbol.toUpperCase();
        int current = stockHoldings.getOrDefault(sym, 0);
        if (current < quantity) {
            throw new InsufficientFundsException(String.format(
                    "Trader %s has insufficient %s shares: required=%d, available=%d",
                    traderId, sym, quantity, current));
        }
        stockHoldings.put(sym, current - quantity);
    }

    public Map<String, Integer> getAllHoldings() {
        return Collections.unmodifiableMap(stockHoldings);
    }

    public boolean canAfford(double amount) {
        return getAvailableCash() >= amount;
    }

    @Override
    public String toString() {
        return String.format("Trader[%s - %s (Cash: $%.2f, Avail: $%.2f)]",
                traderId, name, cashBalance.get(), getAvailableCash());
    }
}
