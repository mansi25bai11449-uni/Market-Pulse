package com.marketpulse.persistence.jdbc;

import com.marketpulse.model.Order;
import com.marketpulse.model.Trade;
import com.marketpulse.persistence.DatabaseConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TradeDAO {
    private final DatabaseConfig dbConfig;
    private final TraderDAO traderDAO;
    private final OrderDAO orderDAO;

    public TradeDAO(DatabaseConfig dbConfig) {
        this.dbConfig = dbConfig;
        this.traderDAO = new TraderDAO(dbConfig);
        this.orderDAO = new OrderDAO(dbConfig);
    }

    private volatile boolean simulateFailureMidway = false;

    public void setSimulateFailureMidway(boolean simulateFailureMidway) {
        this.simulateFailureMidway = simulateFailureMidway;
    }

    public boolean isSimulateFailureMidway() {
        return this.simulateFailureMidway;
    }

    /**
     * Atomically records a trade execution, updates both orders, and adjusts cash balances
     * of both counterparties within a single explicit JDBC database transaction.
     */
    public void recordTradeAtomic(Trade trade, Order buyOrder, Order sellOrder, boolean forceSimulateFailure) throws SQLException {
        String insertTradeSql = """
            INSERT INTO trades (trade_id, buy_order_id, sell_order_id, symbol, price, quantity, executed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        Connection conn = dbConfig.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();

        try {
            conn.setAutoCommit(false);

            // 0. Ensure buy and sell orders exist in DB to satisfy foreign key constraints
            if (buyOrder != null) {
                orderDAO.saveOrder(conn, buyOrder);
            }
            if (sellOrder != null) {
                orderDAO.saveOrder(conn, sellOrder);
            }

            // 1. Insert trade record
            try (PreparedStatement ps = conn.prepareStatement(insertTradeSql)) {
                ps.setString(1, trade.getTradeId());
                ps.setString(2, trade.getBuyOrderId());
                ps.setString(3, trade.getSellOrderId());
                ps.setString(4, trade.getSymbol());
                ps.setDouble(5, trade.getPrice());
                ps.setInt(6, trade.getQuantity());
                ps.setTimestamp(7, Timestamp.from(trade.getExecutedAt()));
                ps.executeUpdate();
            }

            // 2. Update buyer balance (- amount)
            traderDAO.updateBalance(conn, trade.getBuyerId(), -trade.getTotalAmount());

            // Deliberate simulated crash between balance transfers to verify rollback behavior
            if (forceSimulateFailure || this.simulateFailureMidway) {
                throw new SQLException("SIMULATED_FAILURE_MIDWAY: Network crash or database disk error");
            }

            // 3. Update seller balance (+ amount)
            traderDAO.updateBalance(conn, trade.getSellerId(), trade.getTotalAmount());

            // 4. Update order states
            if (buyOrder != null) {
                orderDAO.updateOrder(conn, buyOrder);
            }
            if (sellOrder != null) {
                orderDAO.updateOrder(conn, sellOrder);
            }

            // Commit atomic transaction
            conn.commit();
        } catch (SQLException e) {
            // Explicit rollback ensuring zero partial state
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
            conn.close();
        }
    }

    public void recordTradeAtomic(Trade trade, Order buyOrder, Order sellOrder) throws SQLException {
        recordTradeAtomic(trade, buyOrder, sellOrder, false);
    }

    public List<Trade> findAllTrades() throws SQLException {
        List<Trade> list = new ArrayList<>();
        String sql = """
            SELECT t.*, b.trader_id AS buyer_id, s.trader_id AS seller_id
            FROM trades t
            JOIN orders b ON t.buy_order_id = b.order_id
            JOIN orders s ON t.sell_order_id = s.order_id
            ORDER BY t.executed_at DESC
        """;
        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Trade(
                        rs.getString("trade_id"),
                        rs.getString("buy_order_id"),
                        rs.getString("sell_order_id"),
                        rs.getString("buyer_id"),
                        rs.getString("seller_id"),
                        rs.getString("symbol"),
                        rs.getDouble("price"),
                        rs.getInt("quantity"),
                        rs.getTimestamp("executed_at").toInstant()
                ));
            }
        }
        return list;
    }
}
