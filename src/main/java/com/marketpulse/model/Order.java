package com.marketpulse.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public abstract class Order implements Matchable, Comparable<Order> {
    protected final String orderId;
    protected final String traderId;
    protected final String symbol;
    protected final Side side;
    protected final OrderType orderType;
    protected final double price;
    protected final int quantity;
    protected int remainingQuantity;
    protected OrderStatus status;
    protected final Instant createdAt;

    // Advanced Institutional Order & Microstructure features
    protected final double stopPrice;
    protected final int peakQuantity;      // For ICEBERG: slice display limit
    protected int currentSliceRemaining;   // Remaining qty in currently visible peak
    protected final TimeInForce timeInForce;

    public Order(String orderId, String traderId, String symbol, Side side, OrderType orderType,
                 double price, int quantity, double stopPrice, int peakQuantity, TimeInForce timeInForce) {
        this.orderId = orderId != null ? orderId : UUID.randomUUID().toString();
        this.traderId = Objects.requireNonNull(traderId, "traderId cannot be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol cannot be null").toUpperCase();
        this.side = Objects.requireNonNull(side, "side cannot be null");
        this.orderType = Objects.requireNonNull(orderType, "orderType cannot be null");
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.status = OrderStatus.NEW;
        this.createdAt = Instant.now();
        this.stopPrice = stopPrice;
        this.peakQuantity = (orderType == OrderType.ICEBERG && peakQuantity > 0) ? Math.min(peakQuantity, quantity) : quantity;
        this.currentSliceRemaining = (orderType == OrderType.ICEBERG) ? this.peakQuantity : quantity;
        this.timeInForce = timeInForce != null ? timeInForce : TimeInForce.GTC;
    }

    public Order(String orderId, String traderId, String symbol, Side side, OrderType orderType, double price, int quantity) {
        this(orderId, traderId, symbol, side, orderType, price, quantity, 0.0, 0, TimeInForce.GTC);
    }

    public String getOrderId() { return orderId; }
    public String getTraderId() { return traderId; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public OrderType getOrderType() { return orderType; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public int getRemainingQuantity() { return remainingQuantity; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public double getStopPrice() { return stopPrice; }
    public int getPeakQuantity() { return peakQuantity; }
    public int getCurrentSliceRemaining() { return currentSliceRemaining; }
    public TimeInForce getTimeInForce() { return timeInForce; }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    /**
     * Visible quantity displayed in the Level 2 order book ladder.
     */
    public int getVisibleQuantity() {
        if (orderType == OrderType.ICEBERG) {
            return currentSliceRemaining;
        }
        return remainingQuantity;
    }

    @Override
    public void fill(int filledQty) {
        if (filledQty <= 0) {
            throw new IllegalArgumentException("Fill quantity must be positive");
        }
        if (filledQty > this.remainingQuantity) {
            throw new IllegalArgumentException("Fill quantity " + filledQty + " exceeds remaining quantity " + this.remainingQuantity);
        }
        this.remainingQuantity -= filledQty;
        if (orderType == OrderType.ICEBERG) {
            this.currentSliceRemaining -= filledQty;
        } else {
            this.currentSliceRemaining = this.remainingQuantity;
        }

        if (this.remainingQuantity == 0) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    /**
     * Replenishes the next visible slice of an Iceberg order when currentSliceRemaining drops to 0.
     * Returns true if a new slice was successfully loaded, false if no more remaining quantity exists.
     */
    public boolean replenishIcebergSlice() {
        if (orderType != OrderType.ICEBERG || remainingQuantity <= 0) {
            return false;
        }
        if (currentSliceRemaining <= 0) {
            this.currentSliceRemaining = Math.min(peakQuantity, remainingQuantity);
            return true;
        }
        return false;
    }

    public void cancel() {
        if (this.status == OrderStatus.FILLED) {
            throw new IllegalStateException("Cannot cancel a fully filled order");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public boolean isFilled() {
        return this.status == OrderStatus.FILLED;
    }

    public boolean isCancelled() {
        return this.status == OrderStatus.CANCELLED;
    }

    @Override
    public int compareTo(Order other) {
        // Price-time priority comparator logic within same side
        if (this.side == Side.BUY) {
            // Higher price first
            int priceComp = Double.compare(other.price, this.price);
            if (priceComp != 0) return priceComp;
        } else {
            // Lower price first for SELL
            int priceComp = Double.compare(this.price, other.price);
            if (priceComp != 0) return priceComp;
        }
        // FIFO time priority
        return this.createdAt.compareTo(other.createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order order)) return false;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return String.format("%s[%s %s %s qty=%d (rem=%d, vis=%d) @ %.2f tif=%s by %s]",
                getClass().getSimpleName(), orderId.substring(0, Math.min(8, orderId.length())),
                side, symbol, quantity, remainingQuantity, getVisibleQuantity(), price, timeInForce, traderId);
    }
}
