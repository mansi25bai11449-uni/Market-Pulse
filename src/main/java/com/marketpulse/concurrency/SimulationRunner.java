package com.marketpulse.concurrency;

import com.marketpulse.engine.MatchingEngine;
import com.marketpulse.engine.OrderBook;
import com.marketpulse.model.Stock;
import com.marketpulse.model.Trader;

import java.util.*;
import java.util.concurrent.*;

public class SimulationRunner {

    public static record BenchmarkReport(
            boolean lockingEnabled,
            int numThreads,
            int ordersPerThread,
            int totalOrdersAttempted,
            int totalOrdersProcessed,
            long totalSharesBought,
            long totalSharesSold,
            long shareDiscrepancy,
            long totalTrades,
            int exceptionsCaught,
            long durationMs,
            double throughputOrdersPerSec
    ) {
        public boolean isReconciled() {
            return shareDiscrepancy == 0 && exceptionsCaught == 0;
        }

        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=======================================================\n");
            sb.append("   MARKETPULSE CONCURRENCY BENCHMARK REPORT\n");
            sb.append("=======================================================\n");
            sb.append(String.format("Locking Strategy       : %s\n", lockingEnabled ? "PER-SYMBOL REENTRANT LOCK (ENABLED)" : "UNSYNCHRONIZED (DISABLED)"));
            sb.append(String.format("Worker Threads         : %d\n", numThreads));
            sb.append(String.format("Orders / Thread        : %d\n", ordersPerThread));
            sb.append(String.format("Total Orders Attempted : %d\n", totalOrdersAttempted));
            sb.append(String.format("Total Orders Processed : %d\n", totalOrdersProcessed));
            sb.append(String.format("Total Trades Generated : %d\n", totalTrades));
            sb.append(String.format("Total Shares Bought    : %,d\n", totalSharesBought));
            sb.append(String.format("Total Shares Sold      : %,d\n", totalSharesSold));
            sb.append(String.format("Share Discrepancy      : %,d %s\n", shareDiscrepancy, shareDiscrepancy == 0 ? "[PERFECT MATCH]" : "[MISMATCH DETECTED!]"));
            sb.append(String.format("Exceptions Caught      : %d %s\n", exceptionsCaught, exceptionsCaught == 0 ? "[CLEAN]" : "[RACE CONDITION CRASHES!]"));
            sb.append(String.format("Execution Duration     : %d ms\n", durationMs));
            sb.append(String.format("Throughput             : %.2f orders/sec\n", throughputOrdersPerSec));
            sb.append(String.format("Validation Result      : %s\n", isReconciled() ? "PASSED (100% RECONCILED)" : "FAILED (DATA CORRUPTION / MISMATCH)"));
            sb.append("=======================================================\n");
            return sb.toString();
        }
    }

    public static BenchmarkReport run(String symbol, int numThreads, int ordersPerThread, boolean enableLocking) {
        MatchingEngine.resetInstance();
        MatchingEngine engine = MatchingEngine.getInstance();

        // Register default stock
        engine.registerStock(new Stock(symbol, symbol + " Corp", 150.0));
        OrderBook book = engine.getOrCreateBook(symbol);
        book.setLockingEnabled(enableLocking);

        // Register traders with ample initial balance
        List<Trader> traders = new ArrayList<>();
        for (int i = 1; i <= numThreads; i++) {
            Trader t = new Trader("TRADER_" + i, "Trader #" + i, 10_000_000.0);
            engine.registerTrader(t);
            traders.add(t);
        }

        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<TraderTask.TaskResult>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            TraderTask task = new TraderTask(
                    traders.get(i).getTraderId(),
                    symbol,
                    ordersPerThread,
                    150.0,
                    startGate,
                    engine,
                    12345L + i * 77L
            );
            futures.add(pool.submit(task));
        }

        long startTime = System.currentTimeMillis();
        // Release the latch to start all threads concurrently
        startGate.countDown();

        int processed = 0;
        int exceptions = 0;
        long totalTrades = 0;

        for (var f : futures) {
            try {
                TraderTask.TaskResult res = f.get(10, TimeUnit.SECONDS);
                processed += res.ordersSubmitted();
                exceptions += res.exceptionsCaught();
                totalTrades += res.tradesGenerated();
            } catch (Exception e) {
                exceptions++;
            }
        }

        long duration = Math.max(1, System.currentTimeMillis() - startTime);
        pool.shutdown();

        long bought = book.getTotalSharesBought();
        long sold = book.getTotalSharesSold();
        long discrepancy = Math.abs(bought - sold);
        double throughput = (processed * 1000.0) / duration;

        return new BenchmarkReport(
                enableLocking,
                numThreads,
                ordersPerThread,
                numThreads * ordersPerThread,
                processed,
                bought,
                sold,
                discrepancy,
                totalTrades,
                exceptions,
                duration,
                throughput
        );
    }
}
