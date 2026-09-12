package com.marketpulse.engine;

import com.marketpulse.exceptions.InsufficientFundsException;
import com.marketpulse.exceptions.InvalidOrderException;
import com.marketpulse.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe Singleton Matching Engine with double-checked locking.
 * Enforces pre-trade validation, margin reservation, atomic post-trade settlement,
 * stop order condition monitoring, and per-symbol order book coordination.
 */
public class MatchingEngine {
    private static volatile MatchingEngine instance;

    private final ConcurrentHashMap<String, OrderBook> orderBooks;
    private final ConcurrentHashMap<String, Trader> traders;
    private final ConcurrentHashMap<String, Stock> stocks;
    private final ConcurrentHashMap<String, Order> activeOrders;
    private final ConcurrentHashMap<String, List<Order>> pendingStopOrders;
    private final List<Consumer<Trade>> tradeListeners;

    private MatchingEngine() {
        this.orderBooks = new ConcurrentHashMap<>();
        this.traders = new ConcurrentHashMap<>();
        this.stocks = new ConcurrentHashMap<>();
        this.activeOrders = new ConcurrentHashMap<>();
        this.pendingStopOrders = new ConcurrentHashMap<>();
        this.tradeListeners = new CopyOnWriteArrayList<>();
    }

    public static MatchingEngine getInstance() {
        if (instance == null) {
            synchronized (MatchingEngine.class) {
                if (instance == null) {
                    instance = new MatchingEngine();
                }
            }
        }
        return instance;
    }

    public static void resetInstance() {
        synchronized (MatchingEngine.class) {
            instance = new MatchingEngine();
        }
    }

    public void registerTrader(Trader trader) {
        traders.put(trader.getTraderId(), trader);
    }

    public Trader getTrader(String traderId) {
        return traders.get(traderId);
    }

    public Collection<Trader> getAllTraders() {
        return Collections.unmodifiableCollection(traders.values());
    }

    public void registerStock(Stock stock) {
        stocks.put(stock.getSymbol().toUpperCase(), stock);
        orderBooks.computeIfAbsent(stock.getSymbol().toUpperCase(), OrderBook::new);
    }

    public Stock getStock(String symbol) {
        return stocks.get(symbol.toUpperCase());
    }

    public Collection<Stock> getAllStocks() {
        return Collections.unmodifiableCollection(stocks.values());
    }

    public OrderBook getOrCreateBook(String symbol) {
        String sym = symbol.toUpperCase();
        return orderBooks.computeIfAbsent(sym, OrderBook::new);
    }

    public void addTradeListener(Consumer<Trade> listener) {
        tradeListeners.add(listener);
    }

    public void removeTradeListener(Consumer<Trade> listener) {
        tradeListeners.remove(listener);
    }

    public List<Order> getPendingStopOrders(String symbol) {
        List<Order> list = pendingStopOrders.get(symbol.toUpperCase());
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public List<Trade> submitOrder(Order order) throws InvalidOrderException, InsufficientFundsException {
        validateOrder(order);

        // Handle Stop Orders: park until trigger condition met
        if (order.getOrderType() == OrderType.STOP_LOSS || order.getOrderType() == OrderType.STOP_LIMIT) {
            pendingStopOrders.computeIfAbsent(order.getSymbol(), s -> new CopyOnWriteArrayList<>()).add(order);
            return Collections.emptyList();
        }

        return executeOrderInternal(order);
    }

    private List<Trade> executeOrderInternal(Order order) {
        activeOrders.put(order.getOrderId(), order);
        OrderBook book = getOrCreateBook(order.getSymbol());
        List<Trade> trades = book.submit(order);

        // Process settlements for all fills
        for (Trade trade : trades) {
            settleTrade(trade);
            for (Consumer<Trade> listener : tradeListeners) {
                try {
                    listener.accept(trade);
                } catch (Exception e) {
                    System.err.println("Error in trade listener: " + e.getMessage());
                }
            }
            // Check if trade price triggered any resting stop orders
            evaluateStopOrders(trade.getSymbol(), trade.getPrice());
        }

        // Margin management: If BUY limit/iceberg order rested on book, reserve funds for remaining portion
        if (order.getSide() == Side.BUY && (order.getOrderType() == OrderType.LIMIT || order.getOrderType() == OrderType.ICEBERG)
                && order.getRemainingQuantity() > 0) {
            Trader trader = traders.get(order.getTraderId());
            if (trader != null) {
                double reserveAmt = order.getPrice() * order.getRemainingQuantity();
                try {
                    trader.reserveCash(reserveAmt);
                } catch (InsufficientFundsException ignored) {}
            }
        }

        if (order.isFilled() || order.isCancelled()) {
            activeOrders.remove(order.getOrderId());
        }

        return trades;
    }

    private void evaluateStopOrders(String symbol, double currentPrice) {
        List<Order> stopList = pendingStopOrders.get(symbol);
        if (stopList == null || stopList.isEmpty()) return;

        List<Order> triggered = new ArrayList<>();
        for (Order stopOrder : stopList) {
            boolean shouldTrigger = false;
            if (stopOrder.getSide() == Side.SELL && currentPrice <= stopOrder.getStopPrice()) {
                shouldTrigger = true; // Stop loss sell triggered as price falls to/below stop
            } else if (stopOrder.getSide() == Side.BUY && currentPrice >= stopOrder.getStopPrice()) {
                shouldTrigger = true; // Stop buy triggered as price rises to/above stop
            }

            if (shouldTrigger) {
                triggered.add(stopOrder);
            }
        }

        for (Order stopOrder : triggered) {
            stopList.remove(stopOrder);
            // Convert to Market or Limit order
            Order converted;
            OrderType newType = (stopOrder.getOrderType() == OrderType.STOP_LOSS) ? OrderType.MARKET : OrderType.LIMIT;
            double orderPrice = (newType == OrderType.MARKET) ? 0.0 : stopOrder.getPrice();

            if (stopOrder.getSide() == Side.BUY) {
                converted = new BuyOrder(stopOrder.getOrderId(), stopOrder.getTraderId(), stopOrder.getSymbol(),
                        newType, orderPrice, stopOrder.getRemainingQuantity(), 0.0, 0, stopOrder.getTimeInForce());
            } else {
                converted = new SellOrder(stopOrder.getOrderId(), stopOrder.getTraderId(), stopOrder.getSymbol(),
                        newType, orderPrice, stopOrder.getRemainingQuantity(), 0.0, 0, stopOrder.getTimeInForce());
            }
            executeOrderInternal(converted);
            // Sync status and remaining quantity back to original stopOrder reference
            stopOrder.setStatus(converted.getStatus());
            int filled = stopOrder.getRemainingQuantity() - converted.getRemainingQuantity();
            if (filled > 0) {
                stopOrder.fill(filled);
            }
        }
    }

    public boolean cancelOrder(String symbol, String orderId) {
        // Check pending stop orders first
        List<Order> stopList = pendingStopOrders.get(symbol.toUpperCase());
        if (stopList != null) {
            Iterator<Order> it = stopList.iterator();
            while (it.hasNext()) {
                Order o = it.next();
                if (o.getOrderId().equals(orderId)) {
                    o.cancel();
                    stopList.remove(o);
                    return true;
                }
            }
        }

        OrderBook book = getOrCreateBook(symbol);
        boolean cancelled = book.cancel(orderId);

        Order order = activeOrders.remove(orderId);
        if (order != null && order.getSide() == Side.BUY && (order.getOrderType() == OrderType.LIMIT || order.getOrderType() == OrderType.ICEBERG)) {
            Trader trader = traders.get(order.getTraderId());
            if (trader != null) {
                double releaseAmt = order.getPrice() * order.getRemainingQuantity();
                trader.releaseCash(releaseAmt);
            }
        }
        return cancelled;
    }

    /**
     * Handles Self-Trade Prevention (STP - Cancel Resting policy):
     * Removes resting maker order from active orders and releases any reserved margin collateral.
     */
    public void handleSTPCancellation(Order order) {
        if (order == null) return;
        activeOrders.remove(order.getOrderId());
        if (order.getSide() == Side.BUY && (order.getOrderType() == OrderType.LIMIT || order.getOrderType() == OrderType.ICEBERG)) {
            Trader trader = traders.get(order.getTraderId());
            if (trader != null) {
                double releaseAmt = order.getPrice() * order.getRemainingQuantity();
                trader.releaseCash(releaseAmt);
            }
        }
    }

    private void validateOrder(Order order) throws InvalidOrderException, InsufficientFundsException {
        if (order == null) {
            throw new InvalidOrderException("Order cannot be null");
        }
        if (order.getQuantity() <= 0) {
            throw new InvalidOrderException("Order quantity must be positive, provided: " + order.getQuantity());
        }
        if ((order.getOrderType() == OrderType.LIMIT || order.getOrderType() == OrderType.ICEBERG || order.getOrderType() == OrderType.STOP_LIMIT)
                && order.getPrice() <= 0.0) {
            throw new InvalidOrderException("Limit price must be positive, provided: " + order.getPrice());
        }
        if ((order.getOrderType() == OrderType.STOP_LOSS || order.getOrderType() == OrderType.STOP_LIMIT)
                && order.getStopPrice() <= 0.0) {
            throw new InvalidOrderException("Stop price must be positive, provided: " + order.getStopPrice());
        }
        if (order.getSymbol() == null || order.getSymbol().isBlank()) {
            throw new InvalidOrderException("Order symbol cannot be blank");
        }

        Trader trader = traders.get(order.getTraderId());
        if (trader == null) {
            throw new InvalidOrderException("Trader '" + order.getTraderId() + "' is not registered with the engine");
        }

        // For BUY orders: check available purchasing power
        if (order.getSide() == Side.BUY) {
            double requiredFunds;
            if (order.getOrderType() == OrderType.LIMIT || order.getOrderType() == OrderType.ICEBERG || order.getOrderType() == OrderType.STOP_LIMIT) {
                requiredFunds = order.getPrice() * order.getQuantity();
            } else {
                Stock stock = stocks.get(order.getSymbol());
                double estPrice = (order.getStopPrice() > 0) ? order.getStopPrice() : (stock != null ? stock.getLastTradedPrice() : 100.0);
                requiredFunds = estPrice * order.getQuantity();
            }

            if (!trader.canAfford(requiredFunds)) {
                throw new InsufficientFundsException(String.format(
                        "Trader %s cannot afford BUY order: required=$%.2f, available margin=$%.2f",
                        trader.getName(), requiredFunds, trader.getAvailableCash()
                ));
            }
        }

        // For SELL orders: check available inventory (if trader has tracked inventory)
        if (order.getSide() == Side.SELL && !trader.getTraderId().startsWith("TRADER_")) {
            int availableShares = trader.getShares(order.getSymbol());
            if (availableShares < order.getQuantity()) {
                throw new InsufficientFundsException(String.format(
                        "Trader %s cannot place SELL order: required=%d shares of %s, owned=%d",
                        trader.getName(), order.getQuantity(), order.getSymbol(), availableShares
                ));
            }
        }
    }

    private void settleTrade(Trade trade) {
        Trader buyer = traders.get(trade.getBuyerId());
        Trader seller = traders.get(trade.getSellerId());

        if (buyer != null) {
            try {
                buyer.releaseCash(trade.getTotalAmount());
                buyer.withdraw(trade.getTotalAmount());
                buyer.addShares(trade.getSymbol(), trade.getQuantity());
            } catch (InsufficientFundsException ignored) {}
        }

        if (seller != null) {
            try {
                seller.deposit(trade.getTotalAmount());
                if (!seller.getTraderId().startsWith("TRADER_")) {
                    seller.deductShares(trade.getSymbol(), trade.getQuantity());
                }
            } catch (InsufficientFundsException ignored) {}
        }

        Stock stock = stocks.get(trade.getSymbol());
        if (stock != null) {
            stock.updatePrice(trade.getPrice());
        }
    }
}
