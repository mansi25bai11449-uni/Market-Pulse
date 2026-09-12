package com.marketpulse.audit;

import com.marketpulse.model.Trade;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ReplayEngine {

    public static record ReplaySummary(
            int totalTradesReplayed,
            long totalVolumeShares,
            double totalTurnoverDollars,
            List<Trade> trades
    ) {}

    public static ReplaySummary replayFromCsv(Path csvPath) throws IOException {
        List<Trade> trades = new ArrayList<>();
        long totalShares = 0;
        double totalTurnover = 0.0;

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath.toFile()))) {
            String line = br.readLine(); // Header
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                if (parts.length < 10) continue;

                String tradeId = parts[0];
                String buyOrderId = parts[1];
                String sellOrderId = parts[2];
                String buyerId = parts[3];
                String sellerId = parts[4];
                String symbol = parts[5];
                double price = Double.parseDouble(parts[6]);
                int quantity = Integer.parseInt(parts[7]);
                Instant executedAt = Instant.parse(parts[9]);

                Trade t = new Trade(tradeId, buyOrderId, sellOrderId, buyerId, sellerId, symbol, price, quantity, executedAt);
                trades.add(t);
                totalShares += quantity;
                totalTurnover += (price * quantity);
            }
        }

        return new ReplaySummary(trades.size(), totalShares, totalTurnover, trades);
    }
}
