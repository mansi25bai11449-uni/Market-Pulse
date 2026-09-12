package com.marketpulse.persistence;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConfig {
    private static final String DEFAULT_H2_URL = "jdbc:h2:mem:marketpulse;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private static volatile DatabaseConfig instance;
    private final String jdbcUrl;

    public DatabaseConfig(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl != null ? jdbcUrl : DEFAULT_H2_URL;
        initSchema();
    }

    public static DatabaseConfig getInstance() {
        if (instance == null) {
            synchronized (DatabaseConfig.class) {
                if (instance == null) {
                    instance = new DatabaseConfig(DEFAULT_H2_URL);
                }
            }
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, USER, PASSWORD);
    }

    private void initSchema() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            Path schemaPath = Path.of("schema.sql");
            String sql;
            if (Files.exists(schemaPath)) {
                sql = Files.readString(schemaPath);
            } else {
                sql = """
                    CREATE TABLE IF NOT EXISTS traders (
                        trader_id VARCHAR(64) PRIMARY KEY,
                        name VARCHAR(128) NOT NULL,
                        cash_balance DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    CREATE TABLE IF NOT EXISTS orders (
                        order_id VARCHAR(64) PRIMARY KEY,
                        trader_id VARCHAR(64) NOT NULL,
                        symbol VARCHAR(16) NOT NULL,
                        side VARCHAR(8) NOT NULL,
                        order_type VARCHAR(16) NOT NULL,
                        price DOUBLE PRECISION NOT NULL,
                        quantity INT NOT NULL,
                        remaining_qty INT NOT NULL,
                        status VARCHAR(24) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    CREATE TABLE IF NOT EXISTS trades (
                        trade_id VARCHAR(64) PRIMARY KEY,
                        buy_order_id VARCHAR(64) NOT NULL,
                        sell_order_id VARCHAR(64) NOT NULL,
                        symbol VARCHAR(16) NOT NULL,
                        price DOUBLE PRECISION NOT NULL,
                        quantity INT NOT NULL,
                        executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """;
            }

            for (String statement : sql.split(";")) {
                if (!statement.trim().isEmpty()) {
                    stmt.execute(statement.trim());
                }
            }
        } catch (Exception e) {
            System.err.println("Notice: Schema initialization message: " + e.getMessage());
        }
    }
}
