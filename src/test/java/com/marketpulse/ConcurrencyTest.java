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
