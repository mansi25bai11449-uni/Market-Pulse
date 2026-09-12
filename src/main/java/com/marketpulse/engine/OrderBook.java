package com.marketpulse.engine;

import com.marketpulse.exceptions.StaleOrderException;
import com.marketpulse.model.*;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class OrderBook {
    private final String symbol;
    // Bids: highest price first
    private final NavigableMap<Double, Deque<Order>> bids;
    // Asks: lowest price first
    private final NavigableMap<Double, Deque<Order>> asks;
    // Index for quick order lookup and cancellation
    private final Map<String, Order> orderIndex;

    private final ReentrantLock lock;
    private volatile boolean lockingEnabled = true;

    // Metrics for reconciliation
    private long totalSharesBought = 0;
    private long totalSharesSold = 0;
    private long totalTradesCount = 0;
    private long stpEventsCount = 0;

    public OrderBook(String symbol) {
        this.symbol = Objects.requireNonNull(symbol, "symbol cannot be null").toUpperCase();
        this.bids = new java.util.concurrent.ConcurrentSkipListMap<>(Collections.reverseOrder());
        this.asks = new java.util.concurrent.ConcurrentSkipListMap<>();
        this.orderIndex = new java.util.concurrent.ConcurrentHashMap<>();
        this.lock = new ReentrantLock();
    }

    public String getSymbol() { return symbol; }
    public boolean isLockingEnabled() { return lockingEnabled; }
    public void setLockingEnabled(boolean enabled) { this.lockingEnabled = enabled; }

    public long getTotalSharesBought() { return totalSharesBought; }
    public long getTotalSharesSold() { return totalSharesSold; }
    public long getTotalTradesCount() { return totalTradesCount; }
    public long getStpEventsCount() { return stpEventsCount; }

    public List<Trade> submit(Order incomingOrder) {
        if (incomingOrder == null) {
            throw new IllegalArgumentException("incomingOrder cannot be null");
        }

        if (lockingEnabled) {
            lock.lock();
            try {
                return executeMatching(incomingOrder);
            } finally {
                lock.unlock();
            }
        } else {
            return executeMatching(incomingOrder);
        }
    }

    private List<Trade> executeMatching(Order incoming) {
        List<Trade> generatedTrades = new ArrayList<>();

        // Time-In-Force: FOK (Fill-Or-Kill) pre-check
        if (incoming.getTimeInForce() == TimeInForce.FOK) {
            if (!canFulfillImmediately(incoming)) {
                incoming.setStatus(OrderStatus.CANCELLED);
                return Collections.emptyList();
            }
        }

        if (incoming.getSide() == Side.BUY) {
            matchBuyOrder(incoming, generatedTrades);
        } else {
            matchSellOrder(incoming, generatedTrades);
        }

        // Handle remaining quantity
        if (incoming.getRemainingQuantity() > 0) {
            if (incoming.getTimeInForce() == TimeInForce.IOC || incoming.getOrderType() == OrderType.MARKET) {
                // Immediate-Or-Cancel & Market orders do not rest
                incoming.setStatus(OrderStatus.CANCELLED);
            } else if (incoming.getOrderType() == OrderType.LIMIT || incoming.getOrderType() == OrderType.ICEBERG) {
                restOrder(incoming);
            }
        }

        return generatedTrades;
    }

    /**
     * Checks whether a FOK order can be filled in its entirety against current resting book depth.
     */
    private boolean canFulfillImmediately(Order incoming) {
        int needed = incoming.getRemainingQuantity();
        NavigableMap<Double, Deque<Order>> targetBook = (incoming.getSide() == Side.BUY) ? asks : bids;

        for (Map.Entry<Double, Deque<Order>> entry : targetBook.entrySet()) {
            double price = entry.getKey();
            if (incoming.getOrderType() == OrderType.LIMIT || incoming.getOrderType() == OrderType.ICEBERG) {
                if (incoming.getSide() == Side.BUY && incoming.getPrice() < price) break;
                if (incoming.getSide() == Side.SELL && incoming.getPrice() > price) break;
            }

            for (Order resting : entry.getValue()) {
                if (resting.getTraderId().equals(incoming.getTraderId())) continue; // Skip self-trades
                needed -= resting.getVisibleQuantity();
                if (needed <= 0) return true;
            }
        }
        return false;
    }

    private void matchBuyOrder(Order incomingBuy, List<Trade> trades) {
        Iterator<Map.Entry<Double, Deque<Order>>> askIterator = asks.entrySet().iterator();

        while (askIterator.hasNext() && incomingBuy.getRemainingQuantity() > 0) {
            Map.Entry<Double, Deque<Order>> entry = askIterator.next();
            double askPrice = entry.getKey();
            Deque<Order> queueAtPrice = entry.getValue();

            // Check if price is compatible
            if ((incomingBuy.getOrderType() == OrderType.LIMIT || incomingBuy.getOrderType() == OrderType.ICEBERG)
                    && incomingBuy.getPrice() < askPrice) {
                break;
            }

            Iterator<Order> queueIterator = queueAtPrice.iterator();
            List<Order> replenishedSlices = new ArrayList<>();

            while (queueIterator.hasNext() && incomingBuy.getRemainingQuantity() > 0) {
                Order restingSell = queueIterator.next();

                // Self-Trade Prevention (STP - Cancel Resting policy):
                // If taker and maker belong to the same trader, cancel resting maker order,
                // unreserve collateral/inventory, record STP audit event, and continue matching taker.
                if (restingSell.getTraderId().equals(incomingBuy.getTraderId())) {
                    executeSTPCancel(restingSell, queueIterator);
                    continue;
                }

                if (!incomingBuy.canMatchWith(restingSell)) {
                    continue;
                }

                // Match against available visible quantity of resting order
                int availableResting = restingSell.getVisibleQuantity();
                int matchQuantity = Math.min(incomingBuy.getRemainingQuantity(), availableResting);
                double executionPrice = restingSell.getPrice();

                incomingBuy.fill(matchQuantity);
                restingSell.fill(matchQuantity);

                // Pre-trade refund: release excess reserved collateral due to price improvement
                refundBuyerPriceImprovement(incomingBuy, executionPrice, matchQuantity);

                Trade trade = new Trade(
                        incomingBuy.getOrderId(),
                        restingSell.getOrderId(),
                        incomingBuy.getTraderId(),
                        restingSell.getTraderId(),
                        this.symbol,
                        executionPrice,
                        matchQuantity
                );
                trades.add(trade);

                totalSharesBought += matchQuantity;
                totalSharesSold += matchQuantity;
                totalTradesCount++;

                if (restingSell.isFilled()) {
                    queueIterator.remove();
                    orderIndex.remove(restingSell.getOrderId());
                } else if (restingSell.getOrderType() == OrderType.ICEBERG && restingSell.getVisibleQuantity() == 0) {
                    // Iceberg slice consumed: remove from current position and replenish at back of price level
                    queueIterator.remove();
                    if (restingSell.replenishIcebergSlice()) {
                        replenishedSlices.add(restingSell);
                    }
                }
            }

            // Re-append replenished iceberg slices to end of queue to preserve time priority loss
            for (Order slice : replenishedSlices) {
                queueAtPrice.addLast(slice);
            }

            if (queueAtPrice.isEmpty()) {
                askIterator.remove();
            }
        }
    }

    private void matchSellOrder(Order incomingSell, List<Trade> trades) {
        Iterator<Map.Entry<Double, Deque<Order>>> bidIterator = bids.entrySet().iterator();

        while (bidIterator.hasNext() && incomingSell.getRemainingQuantity() > 0) {
            Map.Entry<Double, Deque<Order>> entry = bidIterator.next();
            double bidPrice = entry.getKey();
            Deque<Order> queueAtPrice = entry.getValue();

            // Check if price is compatible
            if ((incomingSell.getOrderType() == OrderType.LIMIT || incomingSell.getOrderType() == OrderType.ICEBERG)
                    && incomingSell.getPrice() > bidPrice) {
                break;
            }

            Iterator<Order> queueIterator = queueAtPrice.iterator();
            List<Order> replenishedSlices = new ArrayList<>();

            while (queueIterator.hasNext() && incomingSell.getRemainingQuantity() > 0) {
                Order restingBuy = queueIterator.next();

                // Self-Trade Prevention (STP - Cancel Resting policy):
                // If taker and maker belong to the same trader, cancel resting maker order,
                // unreserve collateral/margin, record STP audit event, and continue matching taker.
                if (restingBuy.getTraderId().equals(incomingSell.getTraderId())) {
                    executeSTPCancel(restingBuy, queueIterator);
                    continue;
                }

                if (!incomingSell.canMatchWith(restingBuy)) {
                    continue;
                }

                int availableResting = restingBuy.getVisibleQuantity();
                int matchQuantity = Math.min(incomingSell.getRemainingQuantity(), availableResting);
                double executionPrice = restingBuy.getPrice();

                incomingSell.fill(matchQuantity);
                restingBuy.fill(matchQuantity);

                // Excess collateral refund: release excess reserved margin if resting buy had price improvement
                refundBuyerPriceImprovement(restingBuy, executionPrice, matchQuantity);

                Trade trade = new Trade(
                        restingBuy.getOrderId(),
                        incomingSell.getOrderId(),
                        restingBuy.getTraderId(),
                        incomingSell.getTraderId(),
                        this.symbol,
                        executionPrice,
                        matchQuantity
                );
                trades.add(trade);

                totalSharesBought += matchQuantity;
                totalSharesSold += matchQuantity;
                totalTradesCount++;

                if (restingBuy.isFilled()) {
                    queueIterator.remove();
                    orderIndex.remove(restingBuy.getOrderId());
                } else if (restingBuy.getOrderType() == OrderType.ICEBERG && restingBuy.getVisibleQuantity() == 0) {
                    // Iceberg slice consumed: replenish to back of price queue
                    queueIterator.remove();
                    if (restingBuy.replenishIcebergSlice()) {
                        replenishedSlices.add(restingBuy);
                    }
                }
            }

            for (Order slice : replenishedSlices) {
                queueAtPrice.addLast(slice);
            }

            if (queueAtPrice.isEmpty()) {
                bidIterator.remove();
            }
        }
    }

    private void restOrder(Order order) {
        orderIndex.put(order.getOrderId(), order);
        NavigableMap<Double, Deque<Order>> targetBook = (order.getSide() == Side.BUY) ? bids : asks;
        targetBook.computeIfAbsent(order.getPrice(), p -> new ArrayDeque<>()).addLast(order);
    }

    /**
     * Executes Self-Trade Prevention (STP - Cancel Resting policy):
     * Cancels the resting maker order, unreserves its margin or stock collateral,
     * logs the regulatory STP audit event, and increments the STP counter.
     */
    private void executeSTPCancel(Order resting, Iterator<Order> queueIterator) {
        resting.cancel();
        queueIterator.remove();
        orderIndex.remove(resting.getOrderId());
        stpEventsCount++;

        System.out.printf(Locale.US,
                "[STP AUDIT] Self-Trade Prevention triggered for Trader '%s' on %s. Resting order %s (%s %d @ $%.2f) cancelled. Wash trade eliminated.%n",
                resting.getTraderId(), this.symbol, resting.getOrderId(), resting.getSide(), resting.getRemainingQuantity(), resting.getPrice());

        MatchingEngine engine = MatchingEngine.getInstance();
        if (engine != null) {
            engine.handleSTPCancellation(resting);
        }
    }

    public boolean cancel(String orderId) {
        if (lockingEnabled) {
            lock.lock();
            try {
                return executeCancel(orderId);
            } finally {
                lock.unlock();
            }
        } else {
            return executeCancel(orderId);
        }
    }

    private boolean executeCancel(String orderId) {
        Order order = orderIndex.remove(orderId);
        if (order == null || order.isFilled() || order.isCancelled()) {
            throw new StaleOrderException("Order " + orderId + " cannot be cancelled (does not exist or already terminal)");
        }

        order.cancel();
        NavigableMap<Double, Deque<Order>> targetBook = (order.getSide() == Side.BUY) ? bids : asks;
        Deque<Order> queue = targetBook.get(order.getPrice());
        if (queue != null) {
            queue.remove(order);
            if (queue.isEmpty()) {
                targetBook.remove(order.getPrice());
            }
        }
        return true;
    }

    public OptionalDouble getBestBid() {
        if (lockingEnabled) {
            lock.lock();
            try { return bids.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(bids.firstKey()); }
            finally { lock.unlock(); }
        }
        return bids.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(bids.firstKey());
    }

    public OptionalDouble getBestAsk() {
        if (lockingEnabled) {
            lock.lock();
            try { return asks.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(asks.firstKey()); }
            finally { lock.unlock(); }
        }
        return asks.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(asks.firstKey());
    }

    /**
     * Quantitative Micro-Price Indicator:
     * P_micro = (P_ask * V_bid + P_bid * V_ask) / (V_bid + V_ask)
     * If one side is empty, falls back to best bid or best ask.
     */
    public OptionalDouble calculateMicroPrice() {
        if (lockingEnabled) {
            lock.lock();
            try { return computeMicroPriceInternal(); }
            finally { lock.unlock(); }
        }
        return computeMicroPriceInternal();
    }

    private OptionalDouble computeMicroPriceInternal() {
        if (bids.isEmpty() && asks.isEmpty()) return OptionalDouble.empty();
        if (bids.isEmpty()) return OptionalDouble.of(asks.firstKey());
        if (asks.isEmpty()) return OptionalDouble.of(bids.firstKey());

        double bestBidPrice = bids.firstKey();
        double bestAskPrice = asks.firstKey();

        int bidVolumeAtBest = bids.get(bestBidPrice).stream().mapToInt(Order::getVisibleQuantity).sum();
        int askVolumeAtBest = asks.get(bestAskPrice).stream().mapToInt(Order::getVisibleQuantity).sum();

        int totalVolume = bidVolumeAtBest + askVolumeAtBest;
        if (totalVolume == 0) {
            return OptionalDouble.of((bestBidPrice + bestAskPrice) / 2.0);
        }

        double microPrice = (bestAskPrice * bidVolumeAtBest + bestBidPrice * askVolumeAtBest) / (double) totalVolume;
        return OptionalDouble.of(microPrice);
    }

    /**
     * Order Book Imbalance (OBI) Indicator:
     * OBI = (V_bid - V_ask) / (V_bid + V_ask)
     * Value between -1.0 (pure sell pressure) and +1.0 (pure buy pressure).
     */
    public double calculateOrderBookImbalance(int depthLevels) {
        if (lockingEnabled) {
            lock.lock();
            try { return computeOBIInternal(depthLevels); }
            finally { lock.unlock(); }
        }
        return computeOBIInternal(depthLevels);
    }

    private double computeOBIInternal(int depthLevels) {
        int bidVol = extractDepth(bids, depthLevels).stream().mapToInt(DepthLevel::quantity).sum();
        int askVol = extractDepth(asks, depthLevels).stream().mapToInt(DepthLevel::quantity).sum();

        int total = bidVol + askVol;
        if (total == 0) return 0.0;
        return (double) (bidVol - askVol) / total;
    }

    public static record DepthLevel(double price, int quantity, int orderCount) {}

    public List<DepthLevel> getBidDepth(int limit) {
        if (lockingEnabled) {
            lock.lock();
            try { return extractDepth(bids, limit); }
            finally { lock.unlock(); }
        }
        return extractDepth(bids, limit);
    }

    public List<DepthLevel> getAskDepth(int limit) {
        if (lockingEnabled) {
            lock.lock();
            try { return extractDepth(asks, limit); }
            finally { lock.unlock(); }
        }
        return extractDepth(asks, limit);
    }

    private List<DepthLevel> extractDepth(NavigableMap<Double, Deque<Order>> map, int limit) {
        List<DepthLevel> result = new ArrayList<>();
        int count = 0;
        for (Map.Entry<Double, Deque<Order>> entry : map.entrySet()) {
            if (count++ >= limit) break;
            int totalQty = entry.getValue().stream().mapToInt(Order::getVisibleQuantity).sum();
            result.add(new DepthLevel(entry.getKey(), totalQty, entry.getValue().size()));
        }
        return result;
    }

    /**
     * Refunds price improvement difference (LimitPrice - MakerPrice) * executedQuantity
     * back to the buyer's collateral (available balance) immediately upon match execution.
     */
    private void refundBuyerPriceImprovement(Order buyerOrder, double executionPrice, int executedQuantity) {
        if ((buyerOrder.getOrderType() == OrderType.LIMIT || buyerOrder.getOrderType() == OrderType.ICEBERG)
                && buyerOrder.getPrice() > executionPrice) {
            double refund = (buyerOrder.getPrice() - executionPrice) * executedQuantity;
            if (refund > 0) {
                MatchingEngine engine = MatchingEngine.getInstance();
                if (engine != null) {
                    Trader buyer = engine.getTrader(buyerOrder.getTraderId());
                    if (buyer != null) {
                        buyer.releaseCash(refund);
                    }
                }
            }
        }
    }
}
