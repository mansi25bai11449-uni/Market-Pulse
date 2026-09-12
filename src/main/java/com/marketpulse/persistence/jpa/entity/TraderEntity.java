package com.marketpulse.persistence.jpa.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "traders")
public class TraderEntity {
    @Id
    @Column(name = "trader_id", length = 64)
    private String traderId;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "cash_balance", nullable = false)
    private double cashBalance;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public TraderEntity() {}

    public TraderEntity(String traderId, String name, double cashBalance) {
        this.traderId = traderId;
        this.name = name;
        this.cashBalance = cashBalance;
    }

    public String getTraderId() { return traderId; }
    public void setTraderId(String traderId) { this.traderId = traderId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getCashBalance() { return cashBalance; }
    public void setCashBalance(double cashBalance) { this.cashBalance = cashBalance; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
