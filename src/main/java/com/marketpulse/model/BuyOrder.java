package com.marketpulse.model;

public class BuyOrder extends Order {

    public BuyOrder(String orderId, String traderId, String symbol, OrderType orderType,
                    double price, int quantity, double stopPrice, int peakQuantity, TimeInForce timeInForce) {
        super(orderId, traderId, symbol, Side.BUY, orderType, price, quantity, stopPrice, peakQuantity, timeInForce);
    }

    public BuyOrder(String orderId, String traderId, String symbol, OrderType orderType, double price, int quantity) {
        super(orderId, traderId, symbol, Side.BUY, orderType, price, quantity);
    }

    public BuyOrder(String traderId, String symbol, OrderType orderType, double price, int quantity) {
        this(null, traderId, symbol, orderType, price, quantity);
    }

    @Override
    public boolean canMatchWith(Order other) {
        if (other == null || other.getSide() != Side.SELL || !this.symbol.equalsIgnoreCase(other.getSymbol())) {
            return false;
        }
        if (this.remainingQuantity <= 0 || other.getRemainingQuantity() <= 0) {
            return false;
        }
        // Self-match prevention: Trader cannot trade against themselves
        if (this.traderId.equals(other.getTraderId())) {
            return false;
        }
        // If MARKET order, matches regardless of counterparty price
        if (this.orderType == OrderType.MARKET || other.getOrderType() == OrderType.MARKET) {
            return true;
        }
        // For BUY limit / iceberg order: Buy Price >= Counterparty Sell Price
        return this.price >= other.getPrice();
    }
}
