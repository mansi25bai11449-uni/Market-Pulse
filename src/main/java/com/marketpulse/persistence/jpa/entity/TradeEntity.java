package com.marketpulse.persistence.jpa.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trades")
public class TradeEntity {
    @Id
    @Column(name = "trade_id", length = 64)
    private String tradeId;

    @Column(name = "buy_order_id", length = 64, nullable = false)
    private String buyOrderId;

    @Column(name = "sell_order_id", length = 64, nullable = false)
    private String sellOrderId;

    @Column(name = "symbol", length = 16, nullable = false)
    private String symbol;

    @Column(name = "price", nullable = false)
    private double price;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "executed_at")
    private Instant executedAt = Instant.now();

    public TradeEntity() {}

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }
    public String getBuyOrderId() { return buyOrderId; }
    public void setBuyOrderId(String buyOrderId) { this.buyOrderId = buyOrderId; }
    public String getSellOrderId() { return sellOrderId; }
    public void setSellOrderId(String sellOrderId) { this.sellOrderId = sellOrderId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
}
