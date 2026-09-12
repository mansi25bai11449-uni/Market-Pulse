package com.marketpulse.persistence.jdbc;

import com.marketpulse.model.Trader;
import com.marketpulse.persistence.DatabaseConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TraderDAO {
    private final DatabaseConfig dbConfig;

    public TraderDAO(DatabaseConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    public void saveTrader(Connection conn, Trader trader) throws SQLException {
        String sql = "MERGE INTO traders (trader_id, name, cash_balance) KEY(trader_id) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trader.getTraderId());
            ps.setString(2, trader.getName());
            ps.setDouble(3, trader.getCashBalance());
            ps.executeUpdate();
        }
    }

    public void saveTrader(Trader trader) throws SQLException {
        try (Connection conn = dbConfig.getConnection()) {
            saveTrader(conn, trader);
        }
    }

    public Optional<Trader> findById(String traderId) throws SQLException {
        String sql = "SELECT trader_id, name, cash_balance FROM traders WHERE trader_id = ?";
        try (Connection conn = dbConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, traderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Trader(
                            rs.getString("trader_id"),
                            rs.getString("name"),
                            rs.getDouble("cash_balance")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public List<Trader> findAll() throws SQLException {
        List<Trader> list = new ArrayList<>();
        String sql = "SELECT trader_id, name, cash_balance FROM traders ORDER BY cash_balance DESC";
        try (Connection conn = dbConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Trader(
                        rs.getString("trader_id"),
                        rs.getString("name"),
                        rs.getDouble("cash_balance")
                ));
            }
        }
        return list;
    }

    public void updateBalance(Connection conn, String traderId, double delta) throws SQLException {
        String sql = "UPDATE traders SET cash_balance = cash_balance + ? WHERE trader_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, delta);
            ps.setString(2, traderId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Trader " + traderId + " not found for balance update");
            }
        }
    }
}
