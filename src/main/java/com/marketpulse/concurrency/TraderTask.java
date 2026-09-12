package com.marketpulse.concurrency;

import com.marketpulse.engine.MatchingEngine;
import com.marketpulse.model.*;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

public class TraderTask implements Callable<TraderTask.TaskResult> {
    private final String traderId;
    private final String symbol;
    private final int orderCount;
    private final double basePrice;
    private final CountDownLatch startGate;
    private final MatchingEngine engine;
    private final Random random;

    public static record TaskResult(
            String traderId,
            int ordersSubmitted,
            int tradesGenerated,
            int exceptionsCaught,
            long executionTimeMs,
            long[] latenciesNanos
    ) {}

    public TraderTask(String traderId, String symbol, int orderCount, double basePrice,
                      CountDownLatch startGate, MatchingEngine engine, long seed) {
        this.traderId = traderId;
        this.symbol = symbol;
        this.orderCount = orderCount;
        this.basePrice = basePrice;
        this.startGate = startGate;
        this.engine = engine;
        this.random = new Random(seed);
    }

    @Override
    public TaskResult call() {
        try {
            // Wait for all threads to synchronize before starting
            startGate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TaskResult(traderId, 0, 0, 1, 0, new long[0]);
        }

        long start = System.currentTimeMillis();
        int submitted = 0;
        int tradesCount = 0;
        int exceptions = 0;
        long[] latencies = new long[orderCount];

        for (int i = 0; i < orderCount; i++) {
            Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            OrderType type = (random.nextInt(10) < 8) ? OrderType.LIMIT : OrderType.MARKET;
            // Price variation around base price (-$5.0 to +$5.0) in increments of 0.25
            double delta = (random.nextInt(41) - 20) * 0.25;
            double price = Math.max(1.0, Math.round((basePrice + delta) * 100.0) / 100.0);
            int qty = (random.nextInt(10) + 1) * 10; // 10, 20, ..., 100 shares

            Order order = (side == Side.BUY)
                    ? new BuyOrder(traderId, symbol, type, price, qty)
                    : new SellOrder(traderId, symbol, type, price, qty);

            try {
                long t0 = System.nanoTime();
                var trades = engine.submitOrder(order);
                long t1 = System.nanoTime();
                latencies[submitted] = Math.max(1, t1 - t0);
                tradesCount += trades.size();
                submitted++;
            } catch (Exception e) {
                exceptions++;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        long[] validLatencies = java.util.Arrays.copyOf(latencies, submitted);
        return new TaskResult(traderId, submitted, tradesCount, exceptions, elapsed, validLatencies);
    }
}
