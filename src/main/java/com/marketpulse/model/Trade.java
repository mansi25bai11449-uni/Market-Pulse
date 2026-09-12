package com.marketpulse.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Trade {
    private final String tradeId;
    private final String buyOrderId;
    private final String sellOrderId;
    private final String buyerId;
    private final String sellerId;
    private final String symbol;
    private final double price;
    private final int quantity;
    private final Instant executedAt;

    public Trade(String tradeId, String buyOrderId, String sellOrderId, String buyerId, String sellerId,
                 String symbol, double price, int quantity, Instant executedAt) {
        this.tradeId = tradeId != null ? tradeId : UUID.randomUUID().toString();
        this.buyOrderId = Objects.requireNonNull(buyOrderId);
        this.sellOrderId = Objects.requireNonNull(sellOrderId);
        this.buyerId = Objects.requireNonNull(buyerId);
        this.sellerId = Objects.requireNonNull(sellerId);
        this.symbol = Objects.requireNonNull(symbol).toUpperCase();
        this.price = price;
        this.quantity = quantity;
        this.executedAt = executedAt != null ? executedAt : Instant.now();
    }

    public Trade(String buyOrderId, String sellOrderId, String buyerId, String sellerId,
                 String symbol, double price, int quantity) {
        this(null, buyOrderId, sellOrderId, buyerId, sellerId, symbol, price, quantity, Instant.now());
    }

    public String getTradeId() { return tradeId; }
    public String getBuyOrderId() { return buyOrderId; }
    public String getSellOrderId() { return sellOrderId; }
    public String getBuyerId() { return buyerId; }
    public String getSellerId() { return sellerId; }
    public String getSymbol() { return symbol; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public double getTotalAmount() { return price * quantity; }
    public Instant getExecutedAt() { return executedAt; }

    @Override
    public String toString() {
        return String.format("Trade[%s %s qty=%d @ %.2f (Buyer=%s, Seller=%s) at %s]",
                tradeId.substring(0, Math.min(8, tradeId.length())),
                symbol, quantity, price, buyerId, sellerId, executedAt);
    }
}
