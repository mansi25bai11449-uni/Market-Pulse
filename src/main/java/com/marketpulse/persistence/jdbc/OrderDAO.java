package com.marketpulse.persistence.jdbc;

import com.marketpulse.model.*;
import com.marketpulse.persistence.DatabaseConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OrderDAO {
    private final DatabaseConfig dbConfig;

    public OrderDAO(DatabaseConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    public void saveOrder(Connection conn, Order order) throws SQLException {
        String sql = """
            MERGE INTO orders (order_id, trader_id, symbol, side, order_type, price, quantity, remaining_qty, status, created_at)
            KEY(order_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, order.getOrderId());
            ps.setString(2, order.getTraderId());
            ps.setString(3, order.getSymbol());
            ps.setString(4, order.getSide().name());
            ps.setString(5, order.getOrderType().name());
            ps.setDouble(6, order.getPrice());
            ps.setInt(7, order.getQuantity());
            ps.setInt(8, order.getRemainingQuantity());
            ps.setString(9, order.getStatus().name());
            ps.setTimestamp(10, Timestamp.from(order.getCreatedAt()));
            ps.executeUpdate();
        }
    }

    public void saveOrder(Order order) throws SQLException {
        try (Connection conn = dbConfig.getConnection()) {
            saveOrder(conn, order);
        }
    }

    public void updateOrder(Connection conn, Order order) throws SQLException {
        String sql = "UPDATE orders SET remaining_qty = ?, status = ? WHERE order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, order.getRemainingQuantity());
            ps.setString(2, order.getStatus().name());
            ps.setString(3, order.getOrderId());
            ps.executeUpdate();
        }
    }

    public Optional<Order> findById(String orderId) throws SQLException {
        String sql = "SELECT * FROM orders WHERE order_id = ?";
        try (Connection conn = dbConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToOrder(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Order> findBySymbolAndStatus(String symbol, OrderStatus status) throws SQLException {
        List<Order> list = new ArrayList<>();
        String sql = "SELECT * FROM orders WHERE symbol = ? AND status = ? ORDER BY created_at ASC";
        try (Connection conn = dbConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToOrder(rs));
                }
            }
        }
        return list;
    }

    private Order mapResultSetToOrder(ResultSet rs) throws SQLException {
        String id = rs.getString("order_id");
        String traderId = rs.getString("trader_id");
        String symbol = rs.getString("symbol");
        Side side = Side.valueOf(rs.getString("side"));
        OrderType type = OrderType.valueOf(rs.getString("order_type"));
        double price = rs.getDouble("price");
        int qty = rs.getInt("quantity");

        Order order = (side == Side.BUY)
                ? new BuyOrder(id, traderId, symbol, type, price, qty)
                : new SellOrder(id, traderId, symbol, type, price, qty);

        order.setStatus(OrderStatus.valueOf(rs.getString("status")));
        return order;
    }
}
