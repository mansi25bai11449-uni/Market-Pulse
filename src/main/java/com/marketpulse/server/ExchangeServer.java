package com.marketpulse.server;

import com.marketpulse.engine.MatchingEngine;
import com.marketpulse.engine.OrderBook;
import com.marketpulse.model.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * Embedded High-Performance HTTP Exchange Server.
 * Serves the Cyber-Industrial Web Terminal static assets and provides RESTful endpoints
 * for real-time order submission, cancellation, L2 depth ladder, trades, and quantitative microstructure telemetry.
 */
public class ExchangeServer {
    private final int port;
    private HttpServer server;
    private final MatchingEngine engine;
    private final List<Trade> tradeHistory;
    private final Path webRoot;

    public ExchangeServer(int port, Path webRoot) {
        this.port = port;
        this.webRoot = webRoot;
        this.engine = MatchingEngine.getInstance();
        this.tradeHistory = new CopyOnWriteArrayList<>();

        // Capture all generated trades
        this.engine.addTradeListener(this.tradeHistory::add);
        initDefaultMarketState();
    }

    private void initDefaultMarketState() {
        if (engine.getStock("AAPL") == null) {
            engine.registerStock(new Stock("AAPL", "Apple Inc.", 224.50));
        }
        if (engine.getStock("TSLA") == null) {
            engine.registerStock(new Stock("TSLA", "Tesla Inc.", 248.80));
        }
        if (engine.getStock("NVDA") == null) {
            engine.registerStock(new Stock("NVDA", "Nvidia Corp.", 118.25));
        }
        if (engine.getStock("MSFT") == null) {
            engine.registerStock(new Stock("MSFT", "Microsoft Corp.", 428.10));
        }

        if (engine.getTrader("TRADER_MANSI") == null) {
            Trader mansi = new Trader("TRADER_MANSI", "MANSI KUMARI (Lead Quantitative Portfolio Manager)", 1000000.0);
            mansi.addShares("AAPL", 15000);
            mansi.addShares("TSLA", 8000);
            mansi.addShares("NVDA", 20000);
            mansi.addShares("MSFT", 5000);
            engine.registerTrader(mansi);
        }
        if (engine.getTrader("T1") == null) {
            Trader t1 = new Trader("T1", "MANSI KUMARI (Lead Quantitative Portfolio Manager)", 1000000.0);
            t1.addShares("AAPL", 15000);
            t1.addShares("TSLA", 8000);
            t1.addShares("NVDA", 20000);
            t1.addShares("MSFT", 5000);
            engine.registerTrader(t1);
        }
        if (engine.getTrader("T2") == null) {
            Trader t2 = new Trader("T2", "Bob Smith (HFT Fund)", 2500000.0);
            t2.addShares("AAPL", 25000);
            t2.addShares("TSLA", 12000);
            t2.addShares("NVDA", 15000);
            t2.addShares("MSFT", 10000);
            engine.registerTrader(t2);
        }
        if (engine.getTrader("T3") == null) {
            Trader t3 = new Trader("T3", "Charlie Brown (Retail)", 150000.0);
            t3.addShares("AAPL", 1000);
            t3.addShares("TSLA", 500);
            t3.addShares("NVDA", 800);
            t3.addShares("MSFT", 400);
            engine.registerTrader(t3);
        }

        // Seed initial market depth and realistic trades for terminal visualization
        try {
            // AAPL depth (Reference: $224.50, penny spread $0.01)
            engine.submitOrder(new SellOrder(null, "T2", "AAPL", OrderType.LIMIT, 224.55, 10000, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new SellOrder(null, "T2", "AAPL", OrderType.LIMIT, 224.54, 5000, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new SellOrder(null, "T2", "AAPL", OrderType.LIMIT, 224.53, 3800, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new SellOrder(null, "T2", "AAPL", OrderType.LIMIT, 224.52, 2500, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new SellOrder(null, "T2", "AAPL", OrderType.LIMIT, 224.51, 1200, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "AAPL", OrderType.LIMIT, 224.50, 1500, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "AAPL", OrderType.LIMIT, 224.49, 2800, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "AAPL", OrderType.LIMIT, 224.48, 3500, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "AAPL", OrderType.LIMIT, 224.47, 6000, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "AAPL", OrderType.LIMIT, 224.46, 8500, 0.0, 0, TimeInForce.GTC));
            // Trigger initial trade on AAPL (500 shares @ $224.50)
            engine.submitOrder(new SellOrder(null, "T3", "AAPL", OrderType.LIMIT, 224.50, 500, 0.0, 0, TimeInForce.GTC));

            // TSLA depth (Reference: $248.80)
            engine.submitOrder(new SellOrder(null, "T2", "TSLA", OrderType.LIMIT, 248.90, 4000, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new SellOrder(null, "T2", "TSLA", OrderType.LIMIT, 248.85, 2200, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new SellOrder(null, "T2", "TSLA", OrderType.LIMIT, 248.84, 1500, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new SellOrder(null, "T2", "TSLA", OrderType.LIMIT, 248.82, 800, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "TSLA", OrderType.LIMIT, 248.79, 900, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "TSLA", OrderType.LIMIT, 248.78, 1400, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "TSLA", OrderType.LIMIT, 248.75, 2000, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "TSLA", OrderType.LIMIT, 248.70, 3500, 0.0, 0, TimeInForce.GTC));
            // Trigger initial trade on TSLA (300 shares @ $248.82)
            engine.submitOrder(new BuyOrder(null, "T3", "TSLA", OrderType.LIMIT, 248.82, 300, 0.0, 0, TimeInForce.GTC));

            // NVDA depth (Reference: $118.25)
            engine.submitOrder(new SellOrder(null, "T2", "NVDA", OrderType.LIMIT, 118.35, 12000, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new SellOrder(null, "T2", "NVDA", OrderType.LIMIT, 118.30, 5500, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new SellOrder(null, "T2", "NVDA", OrderType.LIMIT, 118.28, 3500, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new SellOrder(null, "T2", "NVDA", OrderType.LIMIT, 118.27, 2000, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "NVDA", OrderType.LIMIT, 118.24, 2200, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "NVDA", OrderType.LIMIT, 118.23, 3800, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "NVDA", OrderType.LIMIT, 118.22, 6000, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "NVDA", OrderType.LIMIT, 118.20, 15000, 0.0, 0, TimeInForce.GTC));
            // Trigger initial trade on NVDA (600 shares @ $118.24)
            engine.submitOrder(new SellOrder(null, "T3", "NVDA", OrderType.LIMIT, 118.24, 600, 0.0, 0, TimeInForce.GTC));

            // MSFT depth (Reference: $428.10)
            engine.submitOrder(new SellOrder(null, "T2", "MSFT", OrderType.LIMIT, 428.20, 4500, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new SellOrder(null, "T2", "MSFT", OrderType.LIMIT, 428.15, 2400, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new SellOrder(null, "T2", "MSFT", OrderType.LIMIT, 428.14, 1200, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new SellOrder(null, "T2", "MSFT", OrderType.LIMIT, 428.12, 600, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "MSFT", OrderType.LIMIT, 428.08, 700, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "MSFT", OrderType.LIMIT, 428.06, 1100, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "MSFT", OrderType.LIMIT, 428.05, 2000, 0.0, 0, TimeInForce.GTC));
            engine.submitOrder(new BuyOrder(null, "T2", "MSFT", OrderType.LIMIT, 428.00, 5000, 0.0, 0, TimeInForce.GTC));
            // Trigger initial trade on MSFT (200 shares @ $428.12)
            engine.submitOrder(new BuyOrder(null, "T3", "MSFT", OrderType.LIMIT, 428.12, 200, 0.0, 0, TimeInForce.GTC));
        } catch (Exception e) {
            System.err.println("Note: Market state initialization notice: " + e.getMessage());
        }
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // API Endpoints
        server.createContext("/api/orders", new OrdersHandler());
        server.createContext("/api/depth", new DepthHandler());
        server.createContext("/api/trades", new TradesHandler());
        server.createContext("/api/microstructure", new MicrostructureHandler());
        server.createContext("/api/traders", new TradersHandler());
        server.createContext("/api/health", exchange -> sendJsonResponse(exchange, 200, "{\"status\":\"UP\",\"engine\":\"MarketPulse\"}"));

        // Static Web Terminal Handler
        server.createContext("/", new StaticFileHandler());

        server.start();
        System.out.println(">>> MarketPulse Embedded Exchange Server running on http://localhost:" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println(">>> MarketPulse Exchange Server stopped.");
        }
    }

    private class OrdersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                Map<String, String> qparams = parseQuery(exchange.getRequestURI().getQuery());
                String symbol = qparams.get("symbol");
                String traderId = qparams.get("traderId");

                List<Order> matching = new ArrayList<>();
                if (symbol != null && !symbol.isBlank()) {
                    List<Order> stops = engine.getPendingStopOrders(symbol.toUpperCase());
                    for (Order o : stops) {
                        if (isTraderMatch(o.getTraderId(), traderId)) {
                            matching.add(o);
                        }
                    }
                } else {
                    for (Stock stock : engine.getAllStocks()) {
                        for (Order o : engine.getPendingStopOrders(stock.getSymbol())) {
                            if (isTraderMatch(o.getTraderId(), traderId)) {
                                matching.add(o);
                            }
                        }
                    }
                }

                StringBuilder sb = new StringBuilder();
                sb.append("[");
                for (int i = 0; i < matching.size(); i++) {
                    if (i > 0) sb.append(",");
                    Order o = matching.get(i);
                    sb.append(String.format(Locale.US,
                            "{\"orderId\":\"%s\",\"traderId\":\"%s\",\"symbol\":\"%s\",\"side\":\"%s\",\"type\":\"%s\",\"price\":%.2f,\"quantity\":%d,\"remainingQty\":%d,\"stopPrice\":%.2f,\"status\":\"%s\"}",
                            o.getOrderId(), o.getTraderId(), o.getSymbol(), o.getSide(), o.getOrderType(), o.getPrice(), o.getQuantity(), o.getRemainingQuantity(), o.getStopPrice(), o.getStatus()));
                }
                sb.append("]");
                sendJsonResponse(exchange, 200, sb.toString());
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseJsonOrForm(body);

                try {
                    String traderId = params.getOrDefault("traderId", "TRADER_MANSI");
                    String symbol = params.getOrDefault("symbol", "AAPL").toUpperCase();
                    Side side = Side.valueOf(params.getOrDefault("side", "BUY").toUpperCase());
                    OrderType orderType = OrderType.valueOf(params.getOrDefault("orderType", "LIMIT").toUpperCase());
                    double price = Double.parseDouble(params.getOrDefault("price", "0.0"));
                    int quantity = Integer.parseInt(params.getOrDefault("quantity", "10"));
                    double stopPrice = Double.parseDouble(params.getOrDefault("stopPrice", "0.0"));
                    int peakQty = Integer.parseInt(params.getOrDefault("peakQuantity", "0"));
                    TimeInForce tif = TimeInForce.valueOf(params.getOrDefault("timeInForce", "GTC").toUpperCase());

                    Order order;
                    if (side == Side.BUY) {
                        order = new BuyOrder(null, traderId, symbol, orderType, price, quantity, stopPrice, peakQty, tif);
                    } else {
                        order = new SellOrder(null, traderId, symbol, orderType, price, quantity, stopPrice, peakQty, tif);
                    }

                    List<Trade> fills = engine.submitOrder(order);

                    StringBuilder sb = new StringBuilder();
                    sb.append("{");
                    sb.append("\"success\":true,");
                    sb.append("\"orderId\":\"").append(order.getOrderId()).append("\",");
                    sb.append("\"status\":\"").append(order.getStatus()).append("\",");
                    sb.append("\"remainingQty\":").append(order.getRemainingQuantity()).append(",");
                    sb.append("\"fillsCount\":").append(fills.size()).append(",");
                    sb.append("\"trades\":[");
                    for (int i = 0; i < fills.size(); i++) {
                        Trade t = fills.get(i);
                        if (i > 0) sb.append(",");
                        sb.append(String.format(Locale.US,
                                "\"tradeId\":\"%s\",\"price\":%.2f,\"quantity\":%d,\"buyer\":\"%s\",\"seller\":\"%s\"",
                                t.getTradeId(), t.getPrice(), t.getQuantity(), t.getBuyerId(), t.getSellerId()));
                        sb.append("}");
                    }
                    sb.append("]}");
                    sendJsonResponse(exchange, 200, sb.toString());
                } catch (Exception e) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            } else if ("DELETE".equalsIgnoreCase(method)) {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> qparams = parseQuery(query);
                String symbol = qparams.getOrDefault("symbol", "AAPL").toUpperCase();
                String orderId = qparams.get("orderId");

                if (orderId == null || orderId.isBlank()) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, String> bparams = parseJsonOrForm(body);
                    if (bparams.containsKey("orderId")) orderId = bparams.get("orderId");
                    if (bparams.containsKey("symbol")) symbol = bparams.get("symbol").toUpperCase();
                }

                if (orderId == null || orderId.isBlank()) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"orderId is required\"}");
                    return;
                }

                try {
                    boolean cancelled = engine.cancelOrder(symbol, orderId);
                    sendJsonResponse(exchange, 200, "{\"success\":" + cancelled + ",\"orderId\":\"" + orderId + "\"}");
                } catch (Exception e) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        private boolean isTraderMatch(String orderTraderId, String requestedTraderId) {
            if (requestedTraderId == null || requestedTraderId.isBlank()) return true;
            if (orderTraderId == null) return false;
            if (orderTraderId.equalsIgnoreCase(requestedTraderId)) return true;
            return (requestedTraderId.equalsIgnoreCase("TRADER_MANSI") || requestedTraderId.equalsIgnoreCase("T1")) &&
                   (orderTraderId.equalsIgnoreCase("TRADER_MANSI") || orderTraderId.equalsIgnoreCase("T1"));
        }
    }

    private class DepthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> qparams = parseQuery(exchange.getRequestURI().getQuery());
            String symbol = qparams.getOrDefault("symbol", "AAPL").toUpperCase();
            int limit = 10;
            try {
                if (qparams.containsKey("limit")) {
                    limit = Integer.parseInt(qparams.get("limit"));
                }
            } catch (Exception ignored) {}

            OrderBook book = engine.getOrCreateBook(symbol);
            List<OrderBook.DepthLevel> bids = book.getBidDepth(limit);
            List<OrderBook.DepthLevel> asks = book.getAskDepth(limit);

            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"symbol\":\"").append(symbol).append("\",");
            sb.append("\"bids\":[");
            for (int i = 0; i < bids.size(); i++) {
                if (i > 0) sb.append(",");
                OrderBook.DepthLevel d = bids.get(i);
                sb.append(String.format(Locale.US, "{\"price\":%.2f,\"quantity\":%d,\"orders\":%d}", d.price(), d.quantity(), d.orderCount()));
            }
            sb.append("],\"asks\":[");
            for (int i = 0; i < asks.size(); i++) {
                if (i > 0) sb.append(",");
                OrderBook.DepthLevel d = asks.get(i);
                sb.append(String.format(Locale.US, "{\"price\":%.2f,\"quantity\":%d,\"orders\":%d}", d.price(), d.quantity(), d.orderCount()));
            }
            sb.append("]}");

            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class MicrostructureHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> qparams = parseQuery(exchange.getRequestURI().getQuery());
            String symbol = qparams.getOrDefault("symbol", "AAPL").toUpperCase();
            OrderBook book = engine.getOrCreateBook(symbol);

            OptionalDouble bestBid = book.getBestBid();
            OptionalDouble bestAsk = book.getBestAsk();
            OptionalDouble microPrice = book.calculateMicroPrice();
            double obi = book.calculateOrderBookImbalance(5);
            Stock stock = engine.getStock(symbol);
            double lastPrice = stock != null ? stock.getLastTradedPrice() : 0.0;

            double spread = (bestBid.isPresent() && bestAsk.isPresent()) ? (bestAsk.getAsDouble() - bestBid.getAsDouble()) : 0.0;
            double midPrice = (bestBid.isPresent() && bestAsk.isPresent()) ? (bestAsk.getAsDouble() + bestBid.getAsDouble()) / 2.0 : lastPrice;

            String json = String.format(Locale.US,
                    "{\"symbol\":\"%s\",\"lastPrice\":%.2f,\"bestBid\":%s,\"bestAsk\":%s,\"midPrice\":%.2f,\"spread\":%.2f,\"microPrice\":%s,\"orderBookImbalance\":%.4f}",
                    symbol, lastPrice,
                    bestBid.isPresent() ? String.format(Locale.US, "%.2f", bestBid.getAsDouble()) : "null",
                    bestAsk.isPresent() ? String.format(Locale.US, "%.2f", bestAsk.getAsDouble()) : "null",
                    midPrice, spread,
                    microPrice.isPresent() ? String.format(Locale.US, "%.4f", microPrice.getAsDouble()) : "null",
                    obi
            );

            sendJsonResponse(exchange, 200, json);
        }
    }

    private class TradesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> qparams = parseQuery(exchange.getRequestURI().getQuery());
            int limit = 20;
            try {
                if (qparams.containsKey("limit")) {
                    limit = Integer.parseInt(qparams.get("limit"));
                }
            } catch (Exception ignored) {}
            String symbolFilter = qparams.get("symbol");

            List<Trade> list = new ArrayList<>(tradeHistory);
            if (symbolFilter != null && !symbolFilter.isBlank()) {
                list.removeIf(t -> !t.getSymbol().equalsIgnoreCase(symbolFilter));
            }
            int start = Math.max(0, list.size() - limit);
            List<Trade> sub = list.subList(start, list.size());
            Collections.reverse(sub);

            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < sub.size(); i++) {
                if (i > 0) sb.append(",");
                Trade t = sub.get(i);
                sb.append(String.format(Locale.US,
                        "{\"tradeId\":\"%s\",\"symbol\":\"%s\",\"price\":%.2f,\"quantity\":%d,\"buyer\":\"%s\",\"seller\":\"%s\",\"timestamp\":\"%s\"}",
                        t.getTradeId(), t.getSymbol(), t.getPrice(), t.getQuantity(), t.getBuyerId(), t.getSellerId(), t.getExecutedAt().toString()));
            }
            sb.append("]");

            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class TradersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int idx = 0;
            for (Trader t : engine.getAllTraders()) {
                if (idx++ > 0) sb.append(",");
                sb.append(String.format(Locale.US,
                        "{\"traderId\":\"%s\",\"name\":\"%s\",\"cash\":%.2f,\"reserved\":%.2f,\"available\":%.2f}",
                        t.getTraderId(), escapeJson(t.getName()), t.getCashBalance(), t.getReservedCash(), t.getAvailableCash()));
            }
            sb.append("]");

            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isBlank()) {
                path = "/index.html";
            }

            Path filePath = webRoot.resolve(path.substring(1)).normalize();
            if (!filePath.startsWith(webRoot) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html; charset=UTF-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".css")) contentType = "text/css; charset=UTF-8";
            else if (path.endsWith(".json")) contentType = "application/json; charset=UTF-8";
            else if (path.endsWith(".png")) contentType = "image/png";

            byte[] bytes = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            } else if (kv.length == 1) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), "");
            }
        }
        return map;
    }

    private Map<String, String> parseJsonOrForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isBlank()) return map;
        body = body.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            // Simple key-value JSON parsing
            String inner = body.substring(1, body.length() - 1);
            String[] parts = inner.split(",");
            for (String part : parts) {
                String[] kv = part.split(":", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim().replace("\"", "");
                    String v = kv[1].trim().replace("\"", "");
                    map.put(k, v);
                }
            }
        } else {
            map.putAll(parseQuery(body));
        }
        return map;
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("[WARN] Invalid port '" + args[0] + "'. Defaulting to 8080.");
                port = 8080;
            }
        }
        Path webDir = Paths.get("web");
        try {
            ExchangeServer server = new ExchangeServer(port, webDir);
            CountDownLatch shutdownLatch = new CountDownLatch(1);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n>>> Shutting down MarketPulse Exchange Server...");
                server.stop();
                shutdownLatch.countDown();
            }));

            server.start();
            System.out.println(">>> Press Ctrl+C to terminate the server.");

            // Keep main thread alive until shutdown hook is triggered or interrupted
            shutdownLatch.await();
        } catch (java.net.BindException e) {
            System.err.println("===============================================================================");
            System.err.println("[ERROR] Failed to bind to port " + port + ": Address already in use.");
            System.err.println("        Another instance of MarketPulse or another application is using port " + port + ".");
            System.err.println("        Please terminate that process or run on a different port.");
            System.err.println("===============================================================================");
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(">>> MarketPulse main thread interrupted. Exiting.");
        } catch (Exception e) {
            System.err.println("===============================================================================");
            System.err.println("[ERROR] Unexpected error starting MarketPulse Exchange Server: " + e.getMessage());
            System.err.println("===============================================================================");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
