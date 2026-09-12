package com.marketpulse;

import com.marketpulse.engine.MatchingEngine;
import com.marketpulse.engine.OrderBook;
import com.marketpulse.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

public class AdvancedOrderTest {
    private MatchingEngine engine;
    private OrderBook book;
    private Trader alice;
    private Trader bob;
    private Trader charlie;

    @BeforeEach
    void setUp() {
        MatchingEngine.resetInstance();
        engine = MatchingEngine.getInstance();
        book = engine.getOrCreateBook("AAPL");

        alice = new Trader("T1", "Alice Vance", 100000.0);
        bob = new Trader("T2", "Bob Smith", 100000.0);
        charlie = new Trader("T3", "Charlie Brown", 100000.0);

        alice.addShares("AAPL", 1000);
        bob.addShares("AAPL", 1000);
        charlie.addShares("AAPL", 1000);

        engine.registerTrader(alice);
        engine.registerTrader(bob);
        engine.registerTrader(charlie);
        engine.registerStock(new Stock("AAPL", "Apple Inc.", 150.0));
    }

    @Test
    @DisplayName("Iceberg order: Displays only peak quantity on depth ladder and replenishes upon partial fill")
    void testIcebergReplenishment() throws Exception {
        // Alice posts Iceberg SELL: 100 total shares, peak slice = 25 @ $150.00
        Order icebergSell = new SellOrder(
                "ICE_SELL_1", "T1", "AAPL", OrderType.ICEBERG,
                150.0, 100, 0.0, 25, TimeInForce.GTC
        );

        engine.submitOrder(icebergSell);

        // Visible quantity in depth ladder should be exactly 25
        List<OrderBook.DepthLevel> asks = book.getAskDepth(5);
        assertEquals(1, asks.size());
        assertEquals(25, asks.get(0).quantity(), "Visible depth must match peak quantity (25)");
        assertEquals(100, icebergSell.getRemainingQuantity(), "Total remaining quantity must still be 100");

        // Bob buys 25 shares: fills the first slice
        Order buySlice1 = new BuyOrder("BUY_1", "T2", "AAPL", OrderType.LIMIT, 150.0, 25);
        List<Trade> fills1 = engine.submitOrder(buySlice1);

        assertEquals(1, fills1.size());
        assertEquals(25, fills1.get(0).getQuantity());
        assertEquals(75, icebergSell.getRemainingQuantity(), "75 shares remaining overall");

        // After slice fill, iceberg automatically replenished next slice of 25
        asks = book.getAskDepth(5);
        assertEquals(1, asks.size());
        assertEquals(25, asks.get(0).quantity(), "Next slice of 25 must now be visible on book");
    }

    @Test
    @DisplayName("Iceberg order: Loses time priority to other orders at same price upon slice replenishment")
    void testIcebergQueuePriorityLoss() throws Exception {
        // Alice posts Iceberg SELL: 50 shares, peak slice = 25 @ $150.00
        Order aliceIceberg = new SellOrder(
                "ALICE_ICE", "T1", "AAPL", OrderType.ICEBERG,
                150.0, 50, 0.0, 25, TimeInForce.GTC
        );
        engine.submitOrder(aliceIceberg);

        // Charlie posts regular limit SELL: 20 shares @ $150.00 behind Alice's first slice
        Order charlieSell = new SellOrder("CHARLIE_SELL", "T3", "AAPL", OrderType.LIMIT, 150.0, 20);
        engine.submitOrder(charlieSell);

        // Bob buys 30 shares: should consume Alice's first slice (25), then consume 5 from Charlie (not Alice's replenished slice)
        Order bobBuy = new BuyOrder("BOB_BUY", "T2", "AAPL", OrderType.LIMIT, 150.0, 30);
        List<Trade> fills = engine.submitOrder(bobBuy);

        assertEquals(2, fills.size());
        assertEquals("T1", fills.get(0).getSellerId(), "First 25 fills from Alice's visible slice");
        assertEquals(25, fills.get(0).getQuantity());

        assertEquals("T3", fills.get(1).getSellerId(), "Remaining 5 fills from Charlie due to FIFO queue priority");
        assertEquals(5, fills.get(1).getQuantity());
    }

    @Test
    @DisplayName("Stop-Loss Order: Remains parked until trade occurs at or below stop price, then triggers")
    void testStopLossTrigger() throws Exception {
        // Alice parks a Stop-Loss SELL at $148.00 for 50 shares
        Order stopLossSell = new SellOrder(
                "STOP_SELL_1", "T1", "AAPL", OrderType.STOP_LOSS,
                0.0, 50, 148.0, 0, TimeInForce.GTC
        );
        engine.submitOrder(stopLossSell);

        // Confirm it is parked in pending stop orders and NOT on resting book
        assertEquals(1, engine.getPendingStopOrders("AAPL").size());
        assertEquals(0, book.getAskDepth(5).size(), "Stop order must not rest on active book");

        // Bob places resting BUY at $148.00
        Order bobBid = new BuyOrder("BOB_BID", "T2", "AAPL", OrderType.LIMIT, 148.0, 50);
        engine.submitOrder(bobBid);

        // Charlie places SELL at $148.00 for 10 shares: trades at $148.00, triggering Alice's stop loss!
        Order charlieSell = new SellOrder("CHARLIE_TRIGGER", "T3", "AAPL", OrderType.LIMIT, 148.0, 10);
        List<Trade> triggerTrades = engine.submitOrder(charlieSell);

        assertEquals(1, triggerTrades.size());
        assertEquals(148.0, triggerTrades.get(0).getPrice());

        // Alice's stop order should have triggered into a market sell and filled against Bob's remaining 40 bid!
        assertEquals(0, engine.getPendingStopOrders("AAPL").size(), "Stop order list should now be empty");
        assertEquals(OrderStatus.PARTIALLY_FILLED, stopLossSell.getStatus());
        assertEquals(10, stopLossSell.getRemainingQuantity(), "40 shares filled against Bob's remaining bid");
    }

    @Test
    @DisplayName("Time-In-Force: FOK (Fill-Or-Kill) aborts cleanly if depth is insufficient")
    void testFillOrKillAbortsOnInsufficientDepth() throws Exception {
        // Resting book has only 30 shares at $150.00
        Order sell = new SellOrder("SELL_30", "T1", "AAPL", OrderType.LIMIT, 150.0, 30);
        engine.submitOrder(sell);

        // Bob submits FOK Buy order for 50 shares at $150.00
        Order fokBuy = new BuyOrder(
                "FOK_BUY", "T2", "AAPL", OrderType.LIMIT,
                150.0, 50, 0.0, 0, TimeInForce.FOK
        );
        List<Trade> trades = engine.submitOrder(fokBuy);

        // Must generate 0 trades and be immediately cancelled
        assertTrue(trades.isEmpty(), "FOK must not execute partial fills");
        assertEquals(OrderStatus.CANCELLED, fokBuy.getStatus());
        assertEquals(30, sell.getRemainingQuantity(), "Resting sell order must remain untouched");
    }

    @Test
    @DisplayName("Time-In-Force: IOC (Immediate-Or-Cancel) fills available depth and cancels remainder")
    void testImmediateOrCancelPartialFill() throws Exception {
        // Resting book has 40 shares at $150.00
        Order sell = new SellOrder("SELL_40", "T1", "AAPL", OrderType.LIMIT, 150.0, 40);
        engine.submitOrder(sell);

        // Bob submits IOC Buy order for 100 shares at $150.00
        Order iocBuy = new BuyOrder(
                "IOC_BUY", "T2", "AAPL", OrderType.LIMIT,
                150.0, 100, 0.0, 0, TimeInForce.IOC
        );
        List<Trade> trades = engine.submitOrder(iocBuy);

        assertEquals(1, trades.size());
        assertEquals(40, trades.get(0).getQuantity());
        assertEquals(OrderStatus.CANCELLED, iocBuy.getStatus(), "Unfilled 60 shares must be cancelled");
        assertEquals(60, iocBuy.getRemainingQuantity());
        assertEquals(0, book.getBidDepth(5).size(), "IOC unfilled remainder must not rest on book");
    }

    @Test
    @DisplayName("Microstructure: Accurately calculates Micro-Price and Order Book Imbalance (OBI)")
    void testMicrostructureCalculations() throws Exception {
        // 100 shares bid at $149.00
        engine.submitOrder(new BuyOrder("BID_1", "T1", "AAPL", OrderType.LIMIT, 149.0, 100));
        // 300 shares ask at $151.00
        engine.submitOrder(new SellOrder("ASK_1", "T2", "AAPL", OrderType.LIMIT, 151.0, 300));

        // Micro-Price formula: (P_ask * V_bid + P_bid * V_ask) / (V_bid + V_ask)
        // = (151.0 * 100 + 149.0 * 300) / 400 = (15100 + 44700) / 400 = 59800 / 400 = 149.50
        OptionalDouble microPrice = book.calculateMicroPrice();
        assertTrue(microPrice.isPresent());
        assertEquals(149.50, microPrice.getAsDouble(), 0.001);

        // Order Book Imbalance (OBI) formula: (V_bid - V_ask) / (V_bid + V_ask)
        // = (100 - 300) / 400 = -200 / 400 = -0.50 (heavy selling pressure)
        double obi = book.calculateOrderBookImbalance(5);
        assertEquals(-0.50, obi, 0.001);
    }
}
