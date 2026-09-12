package com.marketpulse.persistence.jpa.repository;

import com.marketpulse.persistence.DatabaseConfig;
import com.marketpulse.persistence.jpa.entity.TraderEntity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Analytical Reporting Repository executing JPQL / SQL analytics:
 * 1. Most active stock by trade volume
 * 2. Trader with highest cash balance
 * 3. Price history / VWAP for a given symbol
 */
public class AnalyticsRepository {
    private final DatabaseConfig dbConfig;

    public AnalyticsRepository(DatabaseConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    public static record MostActiveStock(String symbol, long totalQuantityTraded, long tradeCount) {}
    public static record PricePoint(double price, int quantity, String timestamp) {}

    public Optional<MostActiveStock> findMostActiveStock() throws SQLException {
        // JPQL: "SELECT t.symbol, SUM(t.quantity), COUNT(t) FROM TradeEntity t GROUP BY t.symbol ORDER BY SUM(t.quantity) DESC"
        String sql = """
            SELECT symbol, SUM(quantity) as total_qty, COUNT(*) as trade_cnt
            FROM trades
            GROUP BY symbol
            ORDER BY total_qty DESC
            LIMIT 1
        """;
        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Optional.of(new MostActiveStock(
                        rs.getString("symbol"),
                        rs.getLong("total_qty"),
                        rs.getLong("trade_cnt")
                ));
            }
        }
        return Optional.empty();
    }

    public Optional<TraderEntity> findTopTraderByWealth() throws SQLException {
        // JPQL: "SELECT t FROM TraderEntity t ORDER BY t.cashBalance DESC"
        String sql = "SELECT trader_id, name, cash_balance, created_at FROM traders ORDER BY cash_balance DESC LIMIT 1";
        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                TraderEntity te = new TraderEntity(
                        rs.getString("trader_id"),
                        rs.getString("name"),
                        rs.getDouble("cash_balance")
                );
                te.setCreatedAt(rs.getTimestamp("created_at").toInstant());
                return Optional.of(te);
            }
        }
        return Optional.empty();
    }

    public List<PricePoint> findPriceHistory(String symbol) throws SQLException {
        // JPQL: "SELECT t.price, t.quantity, t.executedAt FROM TradeEntity t WHERE t.symbol = :sym ORDER BY t.executedAt ASC"
        List<PricePoint> history = new ArrayList<>();
        String sql = "SELECT price, quantity, executed_at FROM trades WHERE symbol = ? ORDER BY executed_at ASC";
        try (Connection conn = dbConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    history.add(new PricePoint(
                            rs.getDouble("price"),
                            rs.getInt("quantity"),
                            rs.getTimestamp("executed_at").toString()
                    ));
                }
            }
        }
        return history;
    }
}
