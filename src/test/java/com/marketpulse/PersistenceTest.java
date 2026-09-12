package com.marketpulse;

import com.marketpulse.engine.MatchingEngine;
import com.marketpulse.exceptions.SettlementException;
import com.marketpulse.model.*;
import com.marketpulse.persistence.DatabaseConfig;
import com.marketpulse.persistence.jdbc.OrderDAO;
import com.marketpulse.persistence.jdbc.TradeDAO;
import com.marketpulse.persistence.jdbc.TraderDAO;
import com.marketpulse.persistence.jpa.repository.AnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class PersistenceTest {
    private DatabaseConfig dbConfig;
    private TraderDAO traderDAO;
    private OrderDAO orderDAO;
    private TradeDAO tradeDAO;
    private AnalyticsRepository analyticsRepo;

    @BeforeEach
    void setUp() {
        MatchingEngine.resetInstance();
        dbConfig = new DatabaseConfig("jdbc:h2:mem:testdb_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        traderDAO = new TraderDAO(dbConfig);
        orderDAO = new OrderDAO(dbConfig);
        tradeDAO = new TradeDAO(dbConfig);
        analyticsRepo = new AnalyticsRepository(dbConfig);
    }

    @Test
    @DisplayName("Atomic trade execution commits all balance and trade changes cleanly")
    void testAtomicTradeCommit() throws SQLException {
        Trader buyer = new Trader("B1", "Buyer", 10000.0);
        Trader seller = new Trader("S1", "Seller", 5000.0);
        traderDAO.saveTrader(buyer);
        traderDAO.saveTrader(seller);

        Order buyOrder = new BuyOrder("O1", "B1", "AAPL", OrderType.LIMIT, 150.0, 10);
        Order sellOrder = new SellOrder("O2", "S1", "AAPL", OrderType.LIMIT, 150.0, 10);
        orderDAO.saveOrder(buyOrder);
        orderDAO.saveOrder(sellOrder);

        buyOrder.fill(10);
        sellOrder.fill(10);

        Trade trade = new Trade("O1", "O2", "B1", "S1", "AAPL", 150.0, 10);
        tradeDAO.recordTradeAtomic(trade, buyOrder, sellOrder, false);

        // Verify balances
        Trader updatedBuyer = traderDAO.findById("B1").orElseThrow();
        Trader updatedSeller = traderDAO.findById("S1").orElseThrow();

        // 10 * 150.0 = 1,500.0 transferred
        assertEquals(8500.0, updatedBuyer.getCashBalance(), 0.001);
        assertEquals(6500.0, updatedSeller.getCashBalance(), 0.001);

        // Verify trade inserted
        var trades = tradeDAO.findAllTrades();
        assertEquals(1, trades.size());
    }

    @Test
    @DisplayName("Transaction rollback: simulated mid-transaction crash leaves ZERO partial state")
    void testAtomicTradeRollbackOnFailure() throws SQLException {
        Trader buyer = new Trader("B2", "Buyer 2", 10000.0);
        Trader seller = new Trader("S2", "Seller 2", 5000.0);
        traderDAO.saveTrader(buyer);
        traderDAO.saveTrader(seller);

        Order buyOrder = new BuyOrder("O3", "B2", "AAPL", OrderType.LIMIT, 150.0, 10);
        Order sellOrder = new SellOrder("O4", "S2", "AAPL", OrderType.LIMIT, 150.0, 10);
        orderDAO.saveOrder(buyOrder);
        orderDAO.saveOrder(sellOrder);

        Trade trade = new Trade("O3", "O4", "B2", "S2", "AAPL", 150.0, 10);

        // Deliberately trigger failure midway through transaction
        assertThrows(SQLException.class, () -> {
            tradeDAO.recordTradeAtomic(trade, buyOrder, sellOrder, true);
        });

        // Verify buyer balance was rolled back to original
        Trader postBuyer = traderDAO.findById("B2").orElseThrow();
        assertEquals(10000.0, postBuyer.getCashBalance(), 0.001, "Buyer balance must be completely rolled back");

        // Verify seller balance was untouched
        Trader postSeller = traderDAO.findById("S2").orElseThrow();
        assertEquals(5000.0, postSeller.getCashBalance(), 0.001, "Seller balance must remain untouched");

        // Verify NO trade record was committed
        var trades = tradeDAO.findAllTrades();
        assertTrue(trades.isEmpty(), "No orphaned trade row should exist in database");
    }

    @Test
    @DisplayName("End-to-End: MatchingEngine executes trade directly into JDBC ACID persistence path")
    void testMatchingEngineDirectTransactionalPersistenceSuccess() throws Exception {
        MatchingEngine engine = MatchingEngine.getInstance();
        engine.enableSynchronousPersistence(tradeDAO);

        Trader buyer = new Trader("SYNC_B1", "Sync Buyer", 50000.0);
        Trader seller = new Trader("SYNC_S1", "Sync Seller", 20000.0);
        seller.addShares("AAPL", 50);

        engine.registerTrader(buyer);
        engine.registerTrader(seller);
        traderDAO.saveTrader(buyer);
        traderDAO.saveTrader(seller);

        Stock stock = new Stock("AAPL", "Apple Inc.", 150.0);
        engine.registerStock(stock);

        Order sellOrder = new SellOrder("SO_1", "SYNC_S1", "AAPL", OrderType.LIMIT, 150.0, 10);
        Order buyOrder = new BuyOrder("BO_1", "SYNC_B1", "AAPL", OrderType.LIMIT, 150.0, 10);

        engine.submitOrder(sellOrder);
        var trades = engine.submitOrder(buyOrder);

        assertEquals(1, trades.size());
        assertEquals(150.0, trades.get(0).getPrice());
        assertEquals(10, trades.get(0).getQuantity());

        // Verify relational database trades table contains this executed trade
        var dbTrades = tradeDAO.findAllTrades();
        assertEquals(1, dbTrades.size());
        assertEquals(trades.get(0).getTradeId(), dbTrades.get(0).getTradeId());

        // Verify database balances match in-memory trader balances with 100% precision
        Trader dbBuyer = traderDAO.findById("SYNC_B1").orElseThrow();
        Trader dbSeller = traderDAO.findById("SYNC_S1").orElseThrow();

        assertEquals(buyer.getCashBalance(), dbBuyer.getCashBalance(), 0.001);
        assertEquals(seller.getCashBalance(), dbSeller.getCashBalance(), 0.001);
        assertEquals(48500.0, dbBuyer.getCashBalance(), 0.001);
        assertEquals(21500.0, dbSeller.getCashBalance(), 0.001);

        // Verify shares were transferred in memory
        assertEquals(10, buyer.getShares("AAPL"));
        assertEquals(40, seller.getShares("AAPL"));
    }

    @Test
    @DisplayName("End-to-End: Database crash during matching triggers rollback and prevents in-memory corruption")
    void testMatchingEngineDirectTransactionalPersistenceRollbackOnDbCrash() throws Exception {
        MatchingEngine engine = MatchingEngine.getInstance();
        engine.enableSynchronousPersistence(tradeDAO);

        Trader buyer = new Trader("CRASH_B1", "Crash Buyer", 50000.0);
        Trader seller = new Trader("CRASH_S1", "Crash Seller", 20000.0);
        seller.addShares("AAPL", 50);

        engine.registerTrader(buyer);
        engine.registerTrader(seller);
        traderDAO.saveTrader(buyer);
        traderDAO.saveTrader(seller);

        Stock stock = new Stock("AAPL", "Apple Inc.", 150.0);
        engine.registerStock(stock);

        Order sellOrder = new SellOrder("SO_2", "CRASH_S1", "AAPL", OrderType.LIMIT, 150.0, 10);
        Order buyOrder = new BuyOrder("BO_2", "CRASH_B1", "AAPL", OrderType.LIMIT, 150.0, 10);

        engine.submitOrder(sellOrder);

        // Arm simulated failure on TradeDAO (mid-transaction network/disk crash)
        tradeDAO.setSimulateFailureMidway(true);

        // Submitting matching buy order must trigger SettlementException
        assertThrows(SettlementException.class, () -> {
            engine.submitOrder(buyOrder);
        });

        // Verify database rollback: 0 trade records committed
        var dbTrades = tradeDAO.findAllTrades();
        assertTrue(dbTrades.isEmpty(), "No trade row must exist after rollback");

        // Verify in-memory state was NOT corrupted (zero money taken, zero shares transferred)
        assertEquals(50000.0, buyer.getCashBalance(), 0.001, "Buyer in-memory balance must remain unmutated");
        assertEquals(20000.0, seller.getCashBalance(), 0.001, "Seller in-memory balance must remain unmutated");
        assertEquals(0, buyer.getShares("AAPL"), "Buyer must not receive shares");
        assertEquals(50, seller.getShares("AAPL"), "Seller must not lose shares");

        // Verify database accounts are also untouched
        Trader dbBuyer = traderDAO.findById("CRASH_B1").orElseThrow();
        Trader dbSeller = traderDAO.findById("CRASH_S1").orElseThrow();
        assertEquals(50000.0, dbBuyer.getCashBalance(), 0.001);
        assertEquals(20000.0, dbSeller.getCashBalance(), 0.001);
    }

    @Test
    @DisplayName("JPQL Analytical queries identify wealthiest trader and most active stock")
    void testAnalyticalQueries() throws SQLException {
        traderDAO.saveTrader(new Trader("W1", "Rich Trader", 5000000.0));
        traderDAO.saveTrader(new Trader("W2", "Small Trader", 1000.0));

        var topTrader = analyticsRepo.findTopTraderByWealth();
        assertTrue(topTrader.isPresent());
        assertEquals("W1", topTrader.get().getTraderId());
        assertEquals(5000000.0, topTrader.get().getCashBalance());
    }
}
