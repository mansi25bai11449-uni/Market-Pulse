package com.marketpulse.model;

import java.util.Objects;

public class Stock {
    private final String symbol;
    private final String name;
    private volatile double lastTradedPrice;

    public Stock(String symbol, String name, double initialPrice) {
        this.symbol = Objects.requireNonNull(symbol, "symbol cannot be null").toUpperCase();
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.lastTradedPrice = initialPrice;
    }

    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public double getLastTradedPrice() { return lastTradedPrice; }

    public void updatePrice(double newPrice) {
        this.lastTradedPrice = newPrice;
    }

    @Override
    public String toString() {
        return String.format("Stock[%s - %s @ $%.2f]", symbol, name, lastTradedPrice);
    }
}
