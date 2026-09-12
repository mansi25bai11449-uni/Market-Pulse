package com.marketpulse.model;

public class SellOrder extends Order {

    public SellOrder(String orderId, String traderId, String symbol, OrderType orderType,
                     double price, int quantity, double stopPrice, int peakQuantity, TimeInForce timeInForce) {
        super(orderId, traderId, symbol, Side.SELL, orderType, price, quantity, stopPrice, peakQuantity, timeInForce);
    }

    public SellOrder(String orderId, String traderId, String symbol, OrderType orderType, double price, int quantity) {
        super(orderId, traderId, symbol, Side.SELL, orderType, price, quantity);
    }

    public SellOrder(String traderId, String symbol, OrderType orderType, double price, int quantity) {
        this(null, traderId, symbol, orderType, price, quantity);
    }

    @Override
    public boolean canMatchWith(Order other) {
        if (other == null || other.getSide() != Side.BUY || !this.symbol.equalsIgnoreCase(other.getSymbol())) {
            return false;
        }
        if (this.remainingQuantity <= 0 || other.getRemainingQuantity() <= 0) {
            return false;
        }
        // Self-match prevention
        if (this.traderId.equals(other.getTraderId())) {
            return false;
        }
        // If MARKET order, matches regardless of counterparty price
        if (this.orderType == OrderType.MARKET || other.getOrderType() == OrderType.MARKET) {
            return true;
        }
        // For SELL limit / iceberg order: Sell Price <= Counterparty Buy Price
        return this.price <= other.getPrice();
    }
}
