package com.marketpulse.persistence.jpa.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "trader_id", length = 64, nullable = false)
    private String traderId;

    @Column(name = "symbol", length = 16, nullable = false)
    private String symbol;

    @Column(name = "side", length = 8, nullable = false)
    private String side;

    @Column(name = "order_type", length = 16, nullable = false)
    private String orderType;

    @Column(name = "price", nullable = false)
    private double price;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "remaining_qty", nullable = false)
    private int remainingQuantity;

    @Column(name = "status", length = 24, nullable = false)
    private String status;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public OrderEntity() {}

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getTraderId() { return traderId; }
    public void setTraderId(String traderId) { this.traderId = traderId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(int remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
