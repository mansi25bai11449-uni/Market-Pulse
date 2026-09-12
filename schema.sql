-- MarketPulse Relational Database Schema
-- Compatible with PostgreSQL, MySQL, and H2 (PostgreSQL Mode)

DROP TABLE IF EXISTS trades;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS traders;

-- 1. Traders Table
CREATE TABLE traders (
    trader_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    cash_balance DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Orders Table
CREATE TABLE orders (
    order_id VARCHAR(64) PRIMARY KEY,
    trader_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    side VARCHAR(8) NOT NULL,            -- 'BUY' or 'SELL'
    order_type VARCHAR(16) NOT NULL,      -- 'LIMIT' or 'MARKET'
    price DOUBLE PRECISION NOT NULL,
    quantity INT NOT NULL,
    remaining_qty INT NOT NULL,
    status VARCHAR(24) NOT NULL,         -- 'NEW', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'REJECTED'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trader_id) REFERENCES traders(trader_id) ON DELETE CASCADE
);

-- 3. Trades Table
CREATE TABLE trades (
    trade_id VARCHAR(64) PRIMARY KEY,
    buy_order_id VARCHAR(64) NOT NULL,
    sell_order_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    price DOUBLE PRECISION NOT NULL,
    quantity INT NOT NULL,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (buy_order_id) REFERENCES orders(order_id),
    FOREIGN KEY (sell_order_id) REFERENCES orders(order_id)
);

-- Indexes for high-frequency queries & analytical reporting
CREATE INDEX idx_orders_symbol_status ON orders(symbol, status);
CREATE INDEX idx_orders_trader ON orders(trader_id);
CREATE INDEX idx_trades_symbol ON trades(symbol);
CREATE INDEX idx_trades_executed_at ON trades(executed_at);

-- Initial seed data
INSERT INTO traders (trader_id, name, cash_balance) VALUES
('TRADER_MANSI', 'MANSI KUMARI', 1000000.0),
('T1', 'MANSI KUMARI', 1000000.0),
('T2', 'Bob Smith', 75000.0),
('T3', 'Charlie Brown', 50000.0),
('T4', 'Diana Prince', 150000.0);
