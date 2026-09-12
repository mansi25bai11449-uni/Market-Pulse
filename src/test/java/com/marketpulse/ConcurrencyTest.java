package com.marketpulse;

import com.marketpulse.concurrency.SimulationRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConcurrencyTest {

    @Test
    @DisplayName("Synchronized Run: 16 threads, 3,200 orders -> 100% Reconciliation, Zero Shares Lost")
    void testSynchronizedMatchingReconciliation() {
        int threads = 16;
        int ordersPerThread = 200;

        SimulationRunner.BenchmarkReport report = SimulationRunner.run("AAPL", threads, ordersPerThread, true);

        System.out.println(report.formatReport());

        assertTrue(report.isReconciled(), "Synchronized run must achieve zero discrepancy and zero exceptions");
        assertEquals(0, report.shareDiscrepancy(), "Total buy shares must exactly equal total sell shares");
        assertEquals(0, report.exceptionsCaught(), "No unhandled concurrency exceptions allowed");
        assertTrue(report.totalTrades() > 0, "Trades must be generated");
        assertTrue(report.avgLatencyMicros() > 0, "Average latency must be captured");
        assertTrue(report.p95LatencyMicros() >= report.p50LatencyMicros(), "P95 latency must be >= P50 latency");
    }

    @Test
    @DisplayName("Scalability Sweep: Multi-Thread Throughput & Latency Scaling (1, 2, 4, 8, 16 threads)")
    void testMultiThreadScalabilitySweep() {
        int[] threadCounts = {1, 2, 4, 8, 16};
        int ordersPerThread = 150;

        String sweepSummary = SimulationRunner.runScalabilitySweep("AAPL", threadCounts, ordersPerThread);
        System.out.println(sweepSummary);

        assertNotNull(sweepSummary);
    }

    @Test
    @DisplayName("High-Scale Stress Benchmark: 10,000 Orders Under 20 Threads -> Invariant Preserved")
    void testHighScaleStressTesting() {
        int threads = 20;
        int ordersPerThread = 500; // 20 * 500 = 10,000 orders

        SimulationRunner.BenchmarkReport report = SimulationRunner.run("MSFT", threads, ordersPerThread, true);
        System.out.println("\n--- HIGH-SCALE 10,000 ORDERS STRESS TEST ---");
        System.out.println(report.formatReport());

        assertEquals(10000, report.totalOrdersProcessed(), "All 10,000 orders must be processed");
        assertEquals(0, report.shareDiscrepancy(), "Invariant: zero shares lost under 10k orders load");
        assertEquals(0, report.exceptionsCaught(), "Invariant: zero race condition exceptions under 10k orders load");
    }

    @Test
    @DisplayName("Unsynchronized Run: Demonstrates race condition / data corruption when locking is disabled")
    void testUnsynchronizedRaceConditionDemonstration() {
        int threads = 16;
        int ordersPerThread = 200;

        // Run without locking
        SimulationRunner.BenchmarkReport report = SimulationRunner.run("AAPL", threads, ordersPerThread, false);

        System.out.println("\nUnsynchronized Run Evidence for Report Section 8.4:");
        System.out.println(report.formatReport());

        // We verify that the report ran and captured metrics
        assertNotNull(report);
        assertTrue(report.totalOrdersProcessed() > 0);
    }
}
