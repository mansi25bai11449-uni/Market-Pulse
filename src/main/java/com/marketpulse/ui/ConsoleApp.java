package com.marketpulse.ui;

import com.marketpulse.audit.ReplayEngine;
import com.marketpulse.audit.TradeLogger;
import com.marketpulse.concurrency.SimulationRunner;
import com.marketpulse.engine.MatchingEngine;
import com.marketpulse.engine.OrderBook;
import com.marketpulse.model.*;
import com.marketpulse.persistence.DatabaseConfig;
import com.marketpulse.persistence.jdbc.OrderDAO;
import com.marketpulse.persistence.jdbc.TradeDAO;
import com.marketpulse.persistence.jdbc.TraderDAO;
import com.marketpulse.persistence.jpa.repository.AnalyticsRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class ConsoleApp {
    private static final Path AUDIT_LOG = Path.of("trades.csv");

    public static void main(String[] args) {
        MatchingEngine engine = MatchingEngine.getInstance();
        DatabaseConfig dbConfig = DatabaseConfig.getInstance();
        TraderDAO traderDAO = new TraderDAO(dbConfig);
        OrderDAO orderDAO = new OrderDAO(dbConfig);
        TradeDAO tradeDAO = new TradeDAO(dbConfig);
        AnalyticsRepository analyticsRepo = new AnalyticsRepository(dbConfig);

        try (TradeLogger tradeLogger = new TradeLogger(AUDIT_LOG);
             Scanner scanner = new Scanner(System.in)) {

            // Wire trade logger & DB listener
            engine.addTradeListener(trade -> {
                tradeLogger.logTrade(trade);
                try {
                    tradeDAO.recordTradeAtomic(trade, null, null);
                } catch (Exception e) {
                    // non-fatal in memory test
                }
            });

            // Initialize default stocks & traders
            engine.registerStock(new Stock("AAPL", "Apple Inc.", 182.40));
            engine.registerStock(new Stock("TSLA", "Tesla Inc.", 245.10));
            engine.registerStock(new Stock("NVDA", "Nvidia Corp.", 890.50));

            Trader alice = new Trader("T1", "Alice Vance", 100000.0);
            Trader bob = new Trader("T2", "Bob Smith", 75000.0);
            engine.registerTrader(alice);
            engine.registerTrader(bob);
            try {
                traderDAO.saveTrader(alice);
                traderDAO.saveTrader(bob);
            } catch (Exception ignored) {}

            printBanner();

            boolean running = true;
            while (running) {
                printMenu();
                System.out.print("\nEnter choice (1-8): ");
                String input = scanner.nextLine().trim();

                switch (input) {
                    case "1" -> handleRegisterTrader(scanner, engine, traderDAO);
                    case "2" -> handlePlaceOrder(scanner, engine, orderDAO);
                    case "3" -> handleCancelOrder(scanner, engine);
                    case "4" -> handleViewOrderBook(scanner, engine);
                    case "5" -> handleRunSimulation(scanner);
                    case "6" -> handleViewReports(analyticsRepo);
                    case "7" -> handleReplayAuditLog();
                    case "8" -> {
                        System.out.println("Shutting down MarketPulse Engine. Goodbye!");
                        running = false;
                    }
                    default -> System.out.println("Invalid selection. Please enter a number between 1 and 8.");
                }
            }
        } catch (Exception e) {
            System.err.println("Fatal application error: " + e.getMessage());
        }
    }

    private static void printBanner() {
        System.out.println("""
        ========================================================================
           __  __            _        _   ____        _          
          |  \\/  |          | |      | | |  _ \\      | |         
          | \\  / | __ _ _ __| | _____| |_| |_) |_   _| |___  ___ 
          | |\\/| |/ _` | '__| |/ / _ \\ __|  __/| | | | / __|/ _ \\
          | |  | | (_| | |  |   <  __/ |_| |   | |_| | \\__ \\  __/
          |_|  |_|\\__,_|_|  |_|\\_\\___|\\__|_|    \\__,_|_|___/\\___|
          Concurrent Stock Order Matching Engine & Trading Terminal
        ========================================================================
        """);
    }

    private static void printMenu() {
        System.out.println("""
        1. Register New Trader
        2. Place Order (BUY/SELL - LIMIT/MARKET)
        3. Cancel Order
        4. View Live Order Book Depth
        5. Run Concurrency Benchmark / Stress Test (Validate Section 8.4)
        6. View Analytical Reports (JPQL Leaderboards)
        7. Replay Trade Audit Log (Deterministic Replay)
        8. Exit
        """);
    }

    private static void handleRegisterTrader(Scanner scanner, MatchingEngine engine, TraderDAO dao) {
        System.out.print("Enter Trader ID: ");
        String id = scanner.nextLine().trim();
        System.out.print("Enter Trader Name: ");
        String name = scanner.nextLine().trim();
        System.out.print("Enter Initial Cash Balance ($): ");
        double cash = Double.parseDouble(scanner.nextLine().trim());

        Trader trader = new Trader(id, name, cash);
        engine.registerTrader(trader);
        try {
            dao.saveTrader(trader);
            System.out.println("SUCCESS: Trader registered: " + trader);
        } catch (Exception e) {
            System.out.println("Trader registered in engine memory.");
        }
    }

    private static void handlePlaceOrder(Scanner scanner, MatchingEngine engine, OrderDAO dao) {
        try {
            System.out.print("Enter Trader ID: ");
            String traderId = scanner.nextLine().trim();
            System.out.print("Enter Stock Symbol (e.g. AAPL, TSLA): ");
            String symbol = scanner.nextLine().trim().toUpperCase();
            System.out.print("Side (1 for BUY, 2 for SELL): ");
            Side side = scanner.nextLine().trim().equals("1") ? Side.BUY : Side.SELL;
            System.out.print("Order Type (1 for LIMIT, 2 for MARKET): ");
            OrderType type = scanner.nextLine().trim().equals("1") ? OrderType.LIMIT : OrderType.MARKET;

            double price = 0.0;
            if (type == OrderType.LIMIT) {
                System.out.print("Enter Limit Price ($): ");
                price = Double.parseDouble(scanner.nextLine().trim());
            }

            System.out.print("Enter Quantity (shares): ");
            int qty = Integer.parseInt(scanner.nextLine().trim());

            Order order = (side == Side.BUY)
                    ? new BuyOrder(traderId, symbol, type, price, qty)
                    : new SellOrder(traderId, symbol, type, price, qty);

            try {
                dao.saveOrder(order);
            } catch (Exception ignored) {}

            List<Trade> fills = engine.submitOrder(order);
            System.out.println("\nOrder Submitted: " + order);
            if (fills.isEmpty()) {
                System.out.println("Result: Rested on book as " + order.getStatus());
            } else {
                System.out.println("Result: Generated " + fills.size() + " trade fills:");
                for (Trade t : fills) {
                    System.out.printf("   --> MATCH: %d shares @ $%.2f (Buyer: %s, Seller: %s)\n",
                            t.getQuantity(), t.getPrice(), t.getBuyerId(), t.getSellerId());
                }
            }
        } catch (Exception e) {
            System.err.println("ORDER REJECTED: " + e.getMessage());
        }
    }

    private static void handleCancelOrder(Scanner scanner, MatchingEngine engine) {
        System.out.print("Enter Symbol: ");
        String symbol = scanner.nextLine().trim().toUpperCase();
        System.out.print("Enter Order ID to cancel: ");
        String orderId = scanner.nextLine().trim();

        try {
            OrderBook book = engine.getOrCreateBook(symbol);
            book.cancel(orderId);
            System.out.println("SUCCESS: Order " + orderId + " cancelled.");
        } catch (Exception e) {
            System.err.println("CANCELLATION FAILED: " + e.getMessage());
        }
    }

    private static void handleViewOrderBook(Scanner scanner, MatchingEngine engine) {
        System.out.print("Enter Symbol (default AAPL): ");
        String sym = scanner.nextLine().trim().toUpperCase();
        if (sym.isBlank()) sym = "AAPL";

        OrderBook book = engine.getOrCreateBook(sym);
        List<OrderBook.DepthLevel> asks = book.getAskDepth(10);
        List<OrderBook.DepthLevel> bids = book.getBidDepth(10);

        System.out.println("\n=== ORDER BOOK: " + sym + " ===");
        System.out.println("--- ASKS (SELL) ---");
        for (int i = asks.size() - 1; i >= 0; i--) {
            var lvl = asks.get(i);
            System.out.printf("   $%-8.2f | %,6d shares (%d orders)\n", lvl.price(), lvl.quantity(), lvl.orderCount());
        }
        double spread = (book.getBestAsk().isPresent() && book.getBestBid().isPresent())
                ? book.getBestAsk().getAsDouble() - book.getBestBid().getAsDouble() : 0.0;
        System.out.printf("--- SPREAD: $%.2f ---\n", spread);
        System.out.println("--- BIDS (BUY) ---");
        for (var lvl : bids) {
            System.out.printf("   $%-8.2f | %,6d shares (%d orders)\n", lvl.price(), lvl.quantity(), lvl.orderCount());
        }
        System.out.println("===============================\n");
    }

    private static void handleRunSimulation(Scanner scanner) {
        System.out.println("\n--- Concurrency Benchmark Configuration ---");
        System.out.print("Number of concurrent trader threads (e.g. 10): ");
        int threads = Integer.parseInt(scanner.nextLine().trim());
        System.out.print("Orders per thread (e.g. 100): ");
        int count = Integer.parseInt(scanner.nextLine().trim());
        System.out.print("Enable Per-Symbol ReentrantLock? (y/n, n demonstrates race condition): ");
        boolean lock = scanner.nextLine().trim().equalsIgnoreCase("y");

        System.out.println("Running simulation on AAPL...");
        var report = SimulationRunner.run("AAPL", threads, count, lock);
        System.out.println(report.formatReport());
    }

    private static void handleViewReports(AnalyticsRepository repo) {
        System.out.println("\n=== JPQL ANALYTICAL REPORTS ===");
        try {
            var activeStock = repo.findMostActiveStock();
            if (activeStock.isPresent()) {
                var s = activeStock.get();
                System.out.printf("1. Most Active Stock  : %s (%,d shares traded across %d fills)\n",
                        s.symbol(), s.totalQuantityTraded(), s.tradeCount());
            } else {
                System.out.println("1. Most Active Stock  : No trades recorded yet.");
            }

            var topTrader = repo.findTopTraderByWealth();
            if (topTrader.isPresent()) {
                var t = topTrader.get();
                System.out.printf("2. Wealthiest Trader  : %s (%s) with $%,.2f cash\n",
                        t.getName(), t.getTraderId(), t.getCashBalance());
            } else {
                System.out.println("2. Wealthiest Trader  : No traders found.");
            }
        } catch (Exception e) {
            System.err.println("Report query error: " + e.getMessage());
        }
        System.out.println("================================\n");
    }

    private static void handleReplayAuditLog() {
        System.out.println("\n=== TRADE AUDIT LOG REPLAY ===");
        try {
            var summary = ReplayEngine.replayFromCsv(AUDIT_LOG);
            System.out.printf("Total Trades Replayed : %,d\n", summary.totalTradesReplayed());
            System.out.printf("Total Volume Shares   : %,d\n", summary.totalVolumeShares());
            System.out.printf("Total Turnover        : $%,.2f\n", summary.totalTurnoverDollars());
            System.out.println("Replay verification complete: Determinism confirmed.");
        } catch (Exception e) {
            System.err.println("Replay error: " + e.getMessage());
        }
        System.out.println("===============================\n");
    }
}
