package com.marketpulse.audit;

import com.marketpulse.model.Trade;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TradeLogger implements AutoCloseable {
    private final Path logFilePath;
    private final BufferedWriter writer;
    private final Object writeLock = new Object();

    public TradeLogger(Path logFilePath) throws IOException {
        this.logFilePath = logFilePath;
        boolean exists = Files.exists(logFilePath);
        if (logFilePath.getParent() != null) {
            Files.createDirectories(logFilePath.getParent());
        }
        this.writer = new BufferedWriter(new FileWriter(logFilePath.toFile(), true));
        if (!exists || Files.size(logFilePath) == 0) {
            writer.write("trade_id,buy_order_id,sell_order_id,buyer_id,seller_id,symbol,price,quantity,total_amount,executed_at\n");
            writer.flush();
        }
    }

    public void logTrade(Trade trade) {
        synchronized (writeLock) {
            try {
                String line = String.format("%s,%s,%s,%s,%s,%s,%.2f,%d,%.2f,%s\n",
                        trade.getTradeId(),
                        trade.getBuyOrderId(),
                        trade.getSellOrderId(),
                        trade.getBuyerId(),
                        trade.getSellerId(),
                        trade.getSymbol(),
                        trade.getPrice(),
                        trade.getQuantity(),
                        trade.getTotalAmount(),
                        trade.getExecutedAt().toString()
                );
                writer.write(line);
                writer.flush();
            } catch (IOException e) {
                System.err.println("Audit TradeLogger error: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (writeLock) {
            writer.flush();
            writer.close();
        }
    }

    public Path getLogFilePath() {
        return logFilePath;
    }
}
