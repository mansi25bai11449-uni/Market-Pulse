package com.marketpulse;

import com.marketpulse.engine.MatchingEngine;
import com.marketpulse.engine.OrderBook;
import com.marketpulse.exceptions.InsufficientFundsException;
import com.marketpulse.exceptions.InvalidOrderException;
import com.marketpulse.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrderBookTest {
    private OrderBook book;
    private MatchingEngine engine;
    private Trader alice;
    private Trader bob;
    private Trader charlie;

    @BeforeEach
    void setUp() {
        MatchingEngine.resetInstance();
        engine = MatchingEngine.getInstance();
        book = engine.getOrCreateBook("AAPL");

        alice = new Trader("T1", "Alice", 50000.0);
        bob = new Trader("T2", "Bob", 50000.0);
        charlie = new Trader("T3", "Charlie", 50000.0);

        engine.registerTrader(alice);
        engine.registerTrader(bob);
        engine.registerTrader(charlie);
        engine.registerStock(new Stock("AAPL", "Apple Inc.", 150.0));
    }

    @Test
    @DisplayName("Exact price and quantity match generates 1 full trade")
    void testExactMatch() throws Exception {
        Order sell = new SellOrder("T1", "AAPL", OrderType.LIMIT, 150.0, 100);
        Order buy = new BuyOrder("T2", "AAPL", OrderType.LIMIT, 150.0, 100);

        book.submit(sell);
        List<Trade> trades = book.submit(buy);

        assertEquals(1, trades.size());
        Trade trade = trades.get(0);
        assertEquals(100, trade.getQuantity());
        assertEquals(150.0, trade.getPrice());
        assertEquals(OrderStatus.FILLED, buy.getStatus());
        assertEquals(OrderStatus.FILLED, sell.getStatus());
        assertEquals(0, buy.getRemainingQuantity());
        assertEquals(0, sell.getRemainingQuantity());
    }

    @Test
    @DisplayName("Partial fill leaves resting remainder on book")
    void testPartialFill() throws Exception {
        Order sell = new SellOrder("T1", "AAPL", OrderType.LIMIT, 150.0, 40);
        Order buy = new BuyOrder("T2", "AAPL", OrderType.LIMIT, 150.0, 100);

        book.submit(sell);
        List<Trade> trades = book.submit(buy);

        assertEquals(1, trades.size());
        assertEquals(40, trades.get(0).getQuantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, buy.getStatus());
        assertEquals(60, buy.getRemainingQuantity());
        assertEquals(OrderStatus.FILLED, sell.getStatus());

        // Book depth should show remaining 60 shares for buy
        var bids = book.getBidDepth(5);
        assertEquals(1, bids.size());
        assertEquals(60, bids.get(0).quantity());
    }

    @Test
    @DisplayName("Price priority: Lowest ask is matched first")
    void testPricePriority() throws Exception {
        Order sellHigh = new SellOrder("T1", "AAPL", OrderType.LIMIT, 155.0, 50);
        Order sellLow = new SellOrder("T2", "AAPL", OrderType.LIMIT, 150.0, 50);

        // Submit high sell first, then low sell
        book.submit(sellHigh);
        book.submit(sellLow);

        // Incoming buy willing to pay up to 160
        Order buy = new BuyOrder("T3", "AAPL", OrderType.LIMIT, 160.0, 50);
        List<Trade> trades = book.submit(buy);

        assertEquals(1, trades.size());
        assertEquals(150.0, trades.get(0).getPrice(), "Must match lowest sell price first (150.0)");
        assertEquals("T2", trades.get(0).getSellerId());
    }

    @Test
    @DisplayName("Time priority (FIFO): Orders at same price filled in arrival order")
    void testTimePriorityFIFO() throws Exception {
        Order sell1 = new SellOrder("T1", "AAPL", OrderType.LIMIT, 150.0, 30);
        Order sell2 = new SellOrder("T2", "AAPL", OrderType.LIMIT, 150.0, 30);

        book.submit(sell1);
        book.submit(sell2);

        Order buy = new BuyOrder("T3", "AAPL", OrderType.LIMIT, 150.0, 30);
        List<Trade> trades = book.submit(buy);

        assertEquals(1, trades.size());
        assertEquals("T1", trades.get(0).getSellerId(), "First arrived sell order must be filled first");
    }

    @Test
    @DisplayName("Incompatible prices do not trade")
    void testNoMatchPriceIncompatible() throws Exception {
        Order sell = new SellOrder("T1", "AAPL", OrderType.LIMIT, 155.0, 100);
        Order buy = new BuyOrder("T2", "AAPL", OrderType.LIMIT, 150.0, 100);

        book.submit(sell);
        List<Trade> trades = book.submit(buy);

        assertTrue(trades.isEmpty());
        assertEquals(OrderStatus.NEW, buy.getStatus());
        assertEquals(OrderStatus.NEW, sell.getStatus());
    }

    @Test
    @DisplayName("Market order executes immediately at best resting price")
    void testMarketOrder() throws Exception {
        Order sell = new SellOrder("T1", "AAPL", OrderType.LIMIT, 152.50, 50);
        book.submit(sell);

        Order marketBuy = new BuyOrder("T2", "AAPL", OrderType.MARKET, 0.0, 50);
        List<Trade> trades = book.submit(marketBuy);

        assertEquals(1, trades.size());
        assertEquals(152.50, trades.get(0).getPrice());
        assertEquals(OrderStatus.FILLED, marketBuy.getStatus());
    }

    @Test
    @DisplayName("Invalid order validation throws InvalidOrderException")
    void testValidationFailure() {
        assertThrows(InvalidOrderException.class, () -> {
            engine.submitOrder(new BuyOrder("T1", "AAPL", OrderType.LIMIT, 150.0, -10));
        });

        assertThrows(InvalidOrderException.class, () -> {
            engine.submitOrder(new BuyOrder("T1", "AAPL", OrderType.LIMIT, -5.0, 100));
        });
    }

    @Test
    @DisplayName("Insufficient funds throws InsufficientFundsException")
    void testInsufficientFunds() {
        // Alice only has $50,000, attempts to buy $150,000 worth of stock
        assertThrows(InsufficientFundsException.class, () -> {
            engine.submitOrder(new BuyOrder("T1", "AAPL", OrderType.LIMIT, 1500.0, 100));
        });
    }

    @Test
    @DisplayName("Self-Trade Prevention (STP - Cancel Resting): Prevents wash trades, cancels maker, releases margin, and continues matching")
    void testSelfTradePrevention() throws Exception {
        alice.addShares("AAPL", 500);
        bob.addShares("AAPL", 500);

        // Step 1: Alice places a resting BUY order: 100 shares @ $150.00
        Order restingBuy = new BuyOrder("T1", "AAPL", OrderType.LIMIT, 150.0, 100);
        engine.submitOrder(restingBuy);

        assertEquals(15000.0, alice.getReservedCash(), 0.001, "Margin reserved for resting BUY order");
        assertEquals(35000.0, alice.getAvailableCash(), 0.001, "Available cash after reservation");
        assertEquals(OrderStatus.NEW, restingBuy.getStatus());

        // Step 2: Alice attempts to sell 100 shares @ $150.00 against her own resting order (Wash Trade attempt)
        Order takerSell = new SellOrder("T1", "AAPL", OrderType.LIMIT, 150.0, 100);
        List<Trade> trades = engine.submitOrder(takerSell);

        // Verify STP canceled the resting maker order
        assertEquals(0, trades.size(), "Zero trades executed: wash trading mathematically blocked");
        assertEquals(OrderStatus.CANCELLED, restingBuy.getStatus(), "Resting maker order must be CANCELLED by STP");
        assertEquals(0.0, alice.getReservedCash(), 0.001, "Reserved margin must be fully released upon STP cancellation");
        assertEquals(50000.0, alice.getAvailableCash(), 0.001, "Available margin restored to $50,000.00");
        assertTrue(book.getStpEventsCount() > 0, "STP event counter must increment");

        // Step 3: Verify taker continues matching subsequent price-time depth
        // Alice places resting sell at $155.00
        Order aliceRestingSell = new SellOrder("T1", "AAPL", OrderType.LIMIT, 155.0, 50);
        engine.submitOrder(aliceRestingSell);
        // Bob places resting sell at $155.00 (behind Alice in FIFO queue)
        Order bobRestingSell = new SellOrder("T2", "AAPL", OrderType.LIMIT, 155.0, 50);
        engine.submitOrder(bobRestingSell);

        // Alice places taker BUY at $155.00 for 50 shares
        Order aliceTakerBuy = new BuyOrder("T1", "AAPL", OrderType.LIMIT, 155.0, 50);
        List<Trade> secondRoundTrades = engine.submitOrder(aliceTakerBuy);

        // Alice's resting sell is cancelled by STP, and Alice matches with Bob!
        assertEquals(OrderStatus.CANCELLED, aliceRestingSell.getStatus(), "Alice's resting sell cancelled via STP");
        assertEquals(1, secondRoundTrades.size(), "Taker buy filled against subsequent depth (Bob)");
        assertEquals("T1", secondRoundTrades.get(0).getBuyerId());
        assertEquals("T2", secondRoundTrades.get(0).getSellerId());
        assertEquals(50, secondRoundTrades.get(0).getQuantity());
        assertEquals(155.0, secondRoundTrades.get(0).getPrice());
    }
}
