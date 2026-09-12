package com.marketpulse;

import com.marketpulse.engine.MatchingEngine;
import com.marketpulse.engine.OrderBook;
import com.marketpulse.exceptions.InsufficientFundsException;
import com.marketpulse.exceptions.StaleOrderException;
import com.marketpulse.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TradingWorkflowTest {
    private MatchingEngine engine;
    private Trader alice;
    private Trader bob;

    @BeforeEach
    void setUp() {
        MatchingEngine.resetInstance();
        engine = MatchingEngine.getInstance();
        engine.registerStock(new Stock("AAPL", "Apple Inc.", 180.0));

        alice = new Trader("ALICE", "Alice Vance", 10000.0);
        bob = new Trader("BOB", "Bob Smith", 5000.0);
        bob.addShares("AAPL", 200);

        engine.registerTrader(alice);
        engine.registerTrader(bob);
    }

    @Test
    @DisplayName("Trader Perspective: Margin reservation on resting BUY and release on cancel")
    void testMarginReservationAndCancellation() throws Exception {
        // Alice has $10,000 cash. She submits a resting LIMIT BUY of 20 shares @ 175.0 = $3,500
        Order restingBuy = new BuyOrder("ALICE", "AAPL", OrderType.LIMIT, 175.0, 20);
        List<Trade> fills = engine.submitOrder(restingBuy);

        assertTrue(fills.isEmpty());
        assertEquals(3500.0, alice.getReservedCash(), 0.001);
        assertEquals(6500.0, alice.getAvailableCash(), 0.001);
        assertEquals(10000.0, alice.getCashBalance(), 0.001);

        // Cancel order: margin must be immediately released
        boolean cancelled = engine.cancelOrder("AAPL", restingBuy.getOrderId());
        assertTrue(cancelled);
        assertEquals(0.0, alice.getReservedCash(), 0.001);
        assertEquals(10000.0, alice.getAvailableCash(), 0.001);

        // Second cancel must throw StaleOrderException
        assertThrows(StaleOrderException.class, () -> {
            engine.cancelOrder("AAPL", restingBuy.getOrderId());
        });
    }

    @Test
    @DisplayName("Trader Perspective: Multi-level order book sweep fills across price tiers")
    void testMultiLevelBookSweep() throws Exception {
        // Bob places two asks: 10 shares @ 181.0, and 15 shares @ 182.0
        Order ask1 = new SellOrder("BOB", "AAPL", OrderType.LIMIT, 181.0, 10);
        Order ask2 = new SellOrder("BOB", "AAPL", OrderType.LIMIT, 182.0, 15);
        engine.submitOrder(ask1);
        engine.submitOrder(ask2);

        // Alice places aggressive BUY limit order for 25 shares @ 185.0 (sweeps both levels)
        Order sweepBuy = new BuyOrder("ALICE", "AAPL", OrderType.LIMIT, 185.0, 25);
        List<Trade> trades = engine.submitOrder(sweepBuy);

        assertEquals(2, trades.size(), "Should match across both distinct price levels");
        assertEquals(10, trades.get(0).getQuantity());
        assertEquals(181.0, trades.get(0).getPrice());
        assertEquals(15, trades.get(1).getQuantity());
        assertEquals(182.0, trades.get(1).getPrice());

        // Alice receives 25 shares of AAPL
        assertEquals(25, alice.getShares("AAPL"));
        // Bob's inventory is deducted 25 shares (200 - 25 = 175)
        assertEquals(175, bob.getShares("AAPL"));
    }

    @Test
    @DisplayName("Trader Perspective: Seller cannot sell shares they do not own (inventory check)")
    void testSellerInventoryValidation() {
        // Alice has 0 shares of AAPL, tries to sell 50 shares
        Order nakedSell = new SellOrder("ALICE", "AAPL", OrderType.LIMIT, 180.0, 50);
        assertThrows(InsufficientFundsException.class, () -> {
            engine.submitOrder(nakedSell);
        });
    }

    @Test
    @DisplayName("Trader Perspective: Price improvement collateral refund for taker BUY matching lower resting ask")
    void testPriceImprovementCollateralRefund() throws Exception {
        // 1. Direct OrderBook match execution verification:
        // Bob places resting maker ask: 20 shares @ $150.00
        OrderBook book = engine.getOrCreateBook("AAPL");
        Order makerAsk = new SellOrder("BOB", "AAPL", OrderType.LIMIT, 150.0, 20);
        book.submit(makerAsk);

        // Pre-trade margin reservation for Alice at limit price $155.00 for 20 shares = $3,100.00
        alice.reserveCash(155.0 * 20);
        assertEquals(3100.0, alice.getReservedCash(), 0.001);
        assertEquals(6900.0, alice.getAvailableCash(), 0.001);
        assertEquals(10000.0, alice.getCashBalance(), 0.001);

        // Alice places taker BUY @ $155.00 for 20 shares (matches resting maker ask at $150.00)
        Order takerBuy = new BuyOrder("ALICE", "AAPL", OrderType.LIMIT, 155.0, 20);
        List<Trade> bookTrades = book.submit(takerBuy);

        assertEquals(1, bookTrades.size());
        assertEquals(150.0, bookTrades.get(0).getPrice(), "Must execute at maker price ($150.00)");
        assertEquals(20, bookTrades.get(0).getQuantity());

        // Immediately upon match execution in OrderBook:
        // Price improvement refund = (155.0 - 150.0) * 20 = $100.00 credited back to available balance
        assertEquals(3000.0, alice.getReservedCash(), 0.001, "Reserved margin reduced by $100 refund immediately upon match");
        assertEquals(7000.0, alice.getAvailableCash(), 0.001, "Available balance increased by $100 immediately upon match");

        // 2. End-to-end engine workflow verification with full post-trade settlement
        MatchingEngine.resetInstance();
        engine = MatchingEngine.getInstance();
        engine.registerStock(new Stock("AAPL", "Apple Inc.", 180.0));
        alice = new Trader("ALICE", "Alice Vance", 10000.0);
        bob = new Trader("BOB", "Bob Smith", 5000.0);
        bob.addShares("AAPL", 200);
        engine.registerTrader(alice);
        engine.registerTrader(bob);

        Order restingAsk = new SellOrder("BOB", "AAPL", OrderType.LIMIT, 150.0, 20);
        engine.submitOrder(restingAsk);

        // Pre-trade margin reserved at limit price
        alice.reserveCash(155.0 * 20);
        assertEquals(3100.0, alice.getReservedCash(), 0.001);
        assertEquals(6900.0, alice.getAvailableCash(), 0.001);

        Order engineBuy = new BuyOrder("ALICE", "AAPL", OrderType.LIMIT, 155.0, 20);
        List<Trade> engineTrades = engine.submitOrder(engineBuy);

        assertEquals(1, engineTrades.size());
        assertEquals(150.0, engineTrades.get(0).getPrice());
        assertEquals(20, engineTrades.get(0).getQuantity());

        // Full post-trade settlement check:
        // Cost: 20 * $150.00 = $3,000.00.
        // Refund: $100.00.
        // Reserved margin is completely freed (0.0), no trapped collateral.
        assertEquals(0.0, alice.getReservedCash(), 0.001, "All reserved margin should be released");
        assertEquals(7000.0, alice.getCashBalance(), 0.001, "Cash balance reduced by execution cost $3,000");
        assertEquals(7000.0, alice.getAvailableCash(), 0.001, "Available cash reflects price improvement refund ($7,000)");
        assertEquals(20, alice.getShares("AAPL"), "Alice received 20 shares");
        assertEquals(180, bob.getShares("AAPL"));
        assertEquals(8000.0, bob.getCashBalance(), 0.001);
    }
}
