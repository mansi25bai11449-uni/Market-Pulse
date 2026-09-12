# MarketPulse: Concurrency-Safe Stock Matching Engine & Real-Time Trading Terminal

[![Java 24](https://img.shields.io/badge/Java-24-orange.svg)](https://openjdk.org/projects/jdk/24/)
[![Maven](https://img.shields.io/badge/Maven-3.9.6-blue.svg)](https://maven.apache.org/)
[![Tests](https://img.shields.io/badge/Tests-24%20Passed%20(100%25)-success.svg)](https://github.com/aryanrajsinha8010/TRADING-TERMINAL)
[![License](https://img.shields.io/badge/License-Academic-lightgrey.svg)]()
[![Architecture](https://img.shields.io/badge/Architecture-5--Layer%20Decoupled-cyan.svg)]()

> **Deterministic price-time priority matching with thread-safe per-symbol locks, Stoikov micro-price quantitative analytics, and dual-layer ACID settlement.**

---

## 1. Project Overview

**MarketPulse** is a high-performance, multi-threaded stock order matching engine and electronic trading platform engineered from the ground up in modern **Java 24**. 

In high-frequency financial exchanges such as the National Stock Exchange of India (NSE) or NASDAQ, thousands of independent trader threads submit, adjust, and cancel limit orders at microsecond intervals. Naive, unsynchronized collections fail immediately under this workload—yielding data races, memory corruption, and illegal share discrepancies.

MarketPulse solves this challenge through:
1. **Per-Symbol Lock Striping:** Fine-grained `ReentrantLock` instances per ticker symbol, eliminating cross-symbol thread contention and driving peak throughput to **31,372 orders/second** (7,582.94 sustained avg).
2. **Two-Tier Composite Order Book:** $O(\log n)$ price-level indexing via non-blocking `ConcurrentSkipListMap` combined with $O(1)$ time-priority queues using `ArrayDeque`.
3. **Strict Invariant Verification:** 100% conservation of value ($\Sigma\text{Bought} \equiv \Sigma\text{Sold}$) across all threads with 0 shares lost and 0 unhandled exceptions.
4. **Institutional Execution & Hygiene:** Native support for Iceberg orders, Stop-Loss/Stop-Limit conditional triggers, Time-in-Force (GTC/IOC/FOK), pre-trade buyer price-improvement refunds, and FINRA/SEC-modeled Cancel-Resting Self-Trade Prevention (STP).
5. **Dual-Layer Persistence:** Sub-2ms double-entry trade settlement via raw JDBC transactions (`Connection.setAutoCommit(false)` with atomic rollback) paired with JPA/Hibernate JPQL repositories for real-time analytical reporting.
6. **Embedded Virtual-Thread Web Terminal:** JDK `HttpServer` running on Java 24 Virtual Threads, serving an institutional glassmorphic UI featuring live Level-2 depth ladders, Japanese candlestick OHLC charts (SMA-7), trade sound chimes, and an interactive Academic Rubric & System Defense Inspector.

---

## 2. System Architecture & Design Artefacts

### 2.1 5-Layer Architectural Blueprint

```mermaid
graph TD
    subgraph Layer1["Layer 1: Presentation & Terminal Layer"]
        L1_UI["Glassmorphic Web Terminal<br/>(HTML5 / CSS3 / Vanilla ES6)"]
        L1_Canvas["Dual-Mode Canvas Engine<br/>(Candlestick OHLC + L2 Depth Curve)"]
        L1_Modal["Academic Rubric & Defense Inspector<br/>(Live Invariant Audit)"]
        L1_CLI["ConsoleApp.java<br/>(Color-Coded Terminal Client)"]
    end

    subgraph Layer2["Layer 2: Embedded REST Server Layer"]
        L2_Server["ExchangeServer.java<br/>(JDK HttpServer on Port 8080)"]
        L2_VT["Java 24 Virtual Threads<br/>(Executors.newVirtualThreadPerTaskExecutor)"]
        L2_Endpoints["REST Endpoints<br/>/api/orders &middot; /api/depth &middot; /api/trades &middot; /api/microstructure"]
    end

    subgraph Layer3["Layer 3: Engine & Concurrency Core"]
        L3_Singleton["MatchingEngine.java<br/>(Volatile Double-Checked Locking)"]
        L3_Map["ConcurrentHashMap&lt;Symbol, OrderBook&gt;"]
        L3_Book["OrderBook.java (Per-Symbol ReentrantLock)"]
        L3_DS["ConcurrentSkipListMap &times; ArrayDeque (FIFO)"]
        L3_Adv["Advanced Execution: Iceberg &middot; Stop &middot; STP &middot; IOC/FOK"]
    end

    subgraph Layer4["Layer 4: Domain Model Layer"]
        L4_Model["com.marketpulse.model.*<br/>Order &middot; BuyOrder &middot; SellOrder &middot; Trader &middot; Stock &middot; Trade"]
        L4_Interface["Matchable Interface (Polymorphism)"]
    end

    subgraph Layer5["Layer 5: Dual Persistence & Audit Layer"]
        L5_JDBC["Phase 1: TradeDAO / OrderDAO / TraderDAO<br/>(Raw JDBC, setAutoCommit(false), Atomic Rollback)"]
        L5_JPA["Phase 2: AnalyticsRepository.java<br/>(JPA / Hibernate JPQL VWAP & Rankings)"]
        L5_Audit["Audit Engine: TradeLogger.java (CSV) &middot; ReplayEngine.java"]
        L5_DB[("Embedded In-Memory H2 Database")]
    end

    Layer1 --> Layer2
    Layer2 --> Layer3
    L3_Singleton --> L3_Map
    L3_Map --> L3_Book
    L3_Book --> L3_DS
    L3_Book --> L4_Model
    L3_Book --> Layer5
    L5_JDBC --> L5_DB
    L5_JPA --> L5_DB
```

---

### 2.2 End-to-End Order Workflow Diagram

```mermaid
sequenceDiagram
    autonumber
    actor Trader as Client / Trader
    participant API as ExchangeServer (Virtual Thread)
    participant Engine as MatchingEngine
    participant Book as OrderBook (Symbol Lock)
    participant Model as Trader Model (Margin)
    participant JDBC as TradeDAO (Phase 1 JDBC)
    participant Web as Terminal Broadcast

    Trader->>API: POST /api/orders (BUY 100 AAPL @ $155 LIMIT)
    API->>Engine: placeOrder(order)
    Engine->>Model: Reserve Collateral ($15,500 Cash)
    alt Insufficient Funds
        Model-->>API: InsufficientFundsException (0 Balance Mutation)
        API-->>Trader: 400 Bad Request
    else Valid Collateral
        Engine->>Book: placeOrder(order)
        Book->>Book: lock.lock() (Acquire Per-Symbol Lock)
        Book->>Book: Check Self-Trade Prevention (STP)
        Book->>Book: FIFO Sweep vs Opposing resting Asks ($150.00)
        Book->>Model: Price-Improvement Refund (+$500 to Cash)
        Book->>JDBC: recordTradeAtomic(trade, buyer, seller)
        alt Database Failure
            JDBC-->>JDBC: rollback() (100% State Restoration)
        else Database Success
            JDBC-->>JDBC: commit() (ACID Settlement)
        end
        Book->>Book: lock.unlock()
        API-->>Trader: 200 OK (Status: FILLED, ExecPrice: $150.00)
        API-->>Web: Broadcast Refreshed Depth Ladder & Micro-Price
    end
```

---

### 2.3 Use Case Diagram

```mermaid
graph LR
    actorTrader["Trader / Portfolio Manager"]
    actorAuditor["Exchange Auditor / Quant"]

    subgraph Boundary["MarketPulse Exchange Boundary"]
        UC1["Submit Order<br/>(LIMIT / MARKET / ICEBERG / STOP)"]
        UC2["Cancel Resting Order<br/>(Margin Release)"]
        UC3["Inspect Level-2 Depth<br/>& Stoikov Micro-Price"]
        UC4["Trigger STP<br/>Wash-Trade Cancellation"]
        UC5["Execute Concurrency Benchmark<br/>(16 Threads / 3,200 Orders)"]
        UC6["Replay Historical Trades<br/>(Deterministic Scrubber)"]
        UC7["Query VWAP & Leaderboard<br/>(JPQL Analytics)"]
    end

    actorTrader --> UC1
    actorTrader --> UC2
    actorTrader --> UC3
    actorTrader --> UC4
    actorTrader --> UC5

    actorAuditor --> UC3
    actorAuditor --> UC6
    actorAuditor --> UC7
    actorAuditor --> UC5
```

---

### 2.4 UML Class & Component Diagram

```mermaid
classDiagram
    class Matchable {
        <<interface>>
        +isCompatible(Matchable m) boolean
        +fill(int fillQty) void
    }

    class Order {
        <<abstract>>
        #String orderId
        #String traderId
        #String symbol
        #double price
        #int quantity
        #int remainingQuantity
        #OrderStatus status
        #OrderType type
        #TimeInForce timeInForce
        +getRemainingQuantity() int
        +fill(int fillQty) void
        +cancel() void
    }

    class BuyOrder {
        +isCompatible(Matchable m) boolean
        +getSide() Side
    }

    class SellOrder {
        +isCompatible(Matchable m) boolean
        +getSide() Side
    }

    class OrderBook {
        -String symbol
        -ReentrantLock lock
        -ConcurrentSkipListMap bids
        -ConcurrentSkipListMap asks
        -List pendingStopOrders
        +placeOrder(Order order) List~Trade~
        +cancelOrder(String orderId) boolean
        +getMicroPrice() double
        +getOrderBookImbalance() double
        -matchLimit(Order taker) List~Trade~
        -executeSTPCancel(Order resting) void
        -refundBuyerPriceImprovement(Order taker, Order maker, int qty) void
    }

    class MatchingEngine {
        <<Singleton>>
        -static volatile MatchingEngine instance
        -ConcurrentHashMap orderBooks
        +getInstance() MatchingEngine
        +placeOrder(Order order) List~Trade~
        +cancelOrder(String symbol, String orderId) boolean
        +getOrderBook(String symbol) OrderBook
    }

    class Trader {
        -String traderId
        -String name
        -double cashBalance
        -double reservedCash
        -Map holdings
        -Map reservedHoldings
        +reserveCash(double amount) void
        +releaseReservedCash(double amount) void
        +deductReservedCash(double amount) void
        +addCash(double amount) void
    }

    Matchable <|.. Order
    Order <|-- BuyOrder
    Order <|-- SellOrder
    MatchingEngine o-- OrderBook : 1..* (per symbol)
    OrderBook ..> Order : matches
    OrderBook ..> Trader : updates balance
```

---

### 2.5 Relational Database Schema & ER Diagram

```mermaid
erDiagram
    TRADERS ||--o{ ORDERS : places
    ORDERS ||--o{ TRADES : "participates as buy_order"
    ORDERS ||--o{ TRADES : "participates as sell_order"

    TRADERS {
        VARCHAR(64) trader_id PK "Unique account identifier"
        VARCHAR(128) name "Trader full legal name"
        DOUBLE cash_balance "Available unreserved cash"
        TIMESTAMP created_at "Account creation timestamp"
    }

    ORDERS {
        VARCHAR(64) order_id PK "Unique submission UUID"
        VARCHAR(64) trader_id FK "References TRADERS(trader_id)"
        VARCHAR(16) symbol "Ticker symbol (AAPL, TSLA, etc.)"
        VARCHAR(8) side "BUY or SELL"
        VARCHAR(16) order_type "LIMIT, MARKET, ICEBERG, STOP"
        DOUBLE price "Requested limit / trigger price"
        INT quantity "Original requested shares"
        INT remaining_qty "Unfilled share count"
        VARCHAR(24) status "NEW, PARTIALLY_FILLED, FILLED, CANCELLED"
        TIMESTAMP created_at "Order submission timestamp"
    }

    TRADES {
        VARCHAR(64) trade_id PK "Unique trade execution UUID"
        VARCHAR(64) buy_order_id FK "References ORDERS(order_id)"
        VARCHAR(64) sell_order_id FK "References ORDERS(order_id)"
        DOUBLE price "Final matched execution price"
        INT quantity "Matched share volume"
        TIMESTAMP executed_at "Match execution timestamp"
    }
```

---

## 3. System Features & Modules

### Module 1: Order Validation & Margin Collateral Management
* **Strict Parameter Guards:** Enforces numeric boundary checks ($P > 0, Q > 0$), valid symbols, and non-null order types.
* **Pre-Trade Collateral Reservations:** Deducts unreserved buyer cash ($P \times Q$) into `reservedCash` or reserves share inventory into `reservedHoldings` before submitting to the book.
* **Checked Exception Discipline:** Throws `InvalidOrderException` or `InsufficientFundsException` with zero state corruption upon validation failure.

### Module 2: Concurrency-Safe Order Matching & Execution
* **Price-Time FIFO Priority:** Opposing price tiers swept in strict economic order; equal-price orders processed in $O(1)$ arrival sequence.
* **Cancel-Resting Self-Trade Prevention (STP):** Detects internal wash trades (`maker.traderId == taker.traderId`), immediately cancels the resting order, releases its margin, and continues sweeping.
* **Iceberg Slicing & Replenishment:** Exposes only `peakQuantity` to Level-2 depth; replenishes fresh slices to the back of the queue upon exhaustion.
* **Stop-Loss / Stop-Limit Automata:** Conditional triggers parked in `pendingStopOrders`, firing automatically when continuous `lastTradedPrice` breaches the threshold.
* **Buyer Price-Improvement Collateral Refund:** Automatically returns $(P_{\text{limit}} - P_{\text{maker}}) \times Q_{\text{fill}}$ to the buyer's liquid cash when filled at a better price.

### Module 3: Microstructure Analytics & Dual-Layer Persistence
* **Stoikov Micro-Price Estimator:**
  $$P_{\text{micro}} = \frac{P_{\text{ask}} \cdot V_{\text{bid}} + P_{\text{bid}} \cdot V_{\text{ask}}}{V_{\text{bid}} + V_{\text{ask}}}$$
* **Order Book Imbalance (OBI):**
  $$\text{OBI} = \frac{V_{\text{bid}} - V_{\text{ask}}}{V_{\text{bid}} + V_{\text{ask}}} \in [-1.0, +1.0]$$
* **Phase 1 (JDBC ACID):** Direct transactional updates with `setAutoCommit(false)` guaranteeing sub-2ms settlement and 100% atomic rollback on SQL fault.
* **Phase 2 (JPA/Hibernate):** Asynchronous JPQL queries calculating Volume-Weighted Average Price (VWAP) and trader wealth leaderboards.
* **Audit & Replay:** Append-only CSV audit logs with deterministic historical replay slider in the UI.

---

## 4. Empirical Concurrency Benchmark Proof

To validate the multi-threaded correctness of MarketPulse, a stress benchmark was executed with **16 concurrent worker threads** submitting **3,200 orders**:

| Locking Strategy | Orders Attempted | Orders Processed | Total Shares Bought | Total Shares Sold | Discrepancy (Loss) | Crashes / Exceptions | Throughput | Result |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Disabled (Unsynchronized `TreeMap`)** | 3,200 | 2,862 | 72,070 | 72,120 | **50 shares lost** | **338 Crashes** (`ConcurrentModException`) | ~12,283 ord/sec | **FAILED (Corrupted State)** |
| **Enabled (Per-Symbol `ReentrantLock`)** | 3,200 | **3,200** | 22,200 | 22,200 | **0 shares lost** | **0 Crashes (Clean)** | **7,582 avg / 31,372 peak** | **PASSED (100% Reconciled)** |

* **Average Order Latency:** **0.131 ms/order** (sub-millisecond execution).
* **Conservation Law:** $\Sigma\text{Bought} \equiv \Sigma\text{Sold}$ held with zero divergence.
* **Self-Trade Interventions:** 32 wash trades detected and cancelled in real-time.

---

## 5. Visual Interface Screenshots

### 5.1 Trading Terminal Overview
Real-time Level-2 depth ladder, candlestick chart with SMA-7 overlay, active trade feed, and portfolio overview.

![MarketPulse Trading Terminal](assets/screenshots/terminal_overview.png)

---

### 5.2 Candlestick Time-Series & Order Ticket
Japanese candlestick 1-minute OHLC chart with volume histogram, pre-trade margin estimator, and quick-preset order sizing.

![Candlestick Chart & Order Entry](assets/screenshots/candlestick_order_ticket.png)

---

### 5.3 Academic Rubric & System Defense Inspector
Interactive defense modal verifying FIFO priority, concurrency throughput, dual-persistence guarantees, and Stoikov microstructure formulas live against the running engine.

![Academic Rubric & System Defense Inspector](assets/screenshots/academic_defense_modal.png)

---

### 5.4 Tactile Hotkey Matrix
Overlay HUD displaying tactile hotkeys (`B`, `S`, `L`, `M`, `1-4`, `Space`, `R`, `?`) for rapid keyboard-driven trading.

![Tactile Hotkey Matrix](assets/screenshots/hotkey_matrix_modal.png)

---

### 5.5 Authoritative Maven Test Suite Run
Terminal output proving 24/24 passing unit and concurrency tests with 0 share discrepancy and 100% build success.

![Maven Test Runner Output](assets/screenshots/terminal_tests_run.png)

---

## 6. Technologies & Tools Used

* **Programming Language:** Java 24 (Virtual Threads via JEP 444, Pattern Matching, Record-like immutability)
* **Build Automation & Dependency Management:** Apache Maven 3.9.6
* **Concurrency Core:** `java.util.concurrent` (`ReentrantLock`, `ConcurrentHashMap`, `ConcurrentSkipListMap`, `AtomicLong`, `CountDownLatch`)
* **Persistence & Database:**
  * Embedded H2 Database 2.2+ (In-memory mode `jdbc:h2:mem:marketpulse`)
  * Raw JDBC Transaction Managers (Phase 1)
  * Hibernate ORM 6.4+ / Jakarta Persistence API (Phase 2)
* **Embedded Networking:** JDK `com.sun.net.httpserver.HttpServer` with Virtual Thread Executor
* **Web Frontend:** HTML5, Modern Vanilla CSS3, Vanilla ES6 JavaScript, HTML5 2D Canvas Engine, Web Audio API
* **Testing & Quality Assurance:** JUnit 5 (Jupiter), Maven Surefire Plugin 3.2.5

---

## 7. Installation & Quick Start

### 7.1 Prerequisites
* **Java Development Kit (JDK):** Version 17 or higher (Java 24 recommended).
* **Apache Maven:** Version 3.8.0 or higher (or use the included Maven wrapper `mvnw.cmd`).

Verify your environment:
```bash
java -version
mvn -version
```

### 7.2 Running the Application

#### Option A: Single-Click Launcher (Windows)
Double-click [`run.bat`](file:///E:/flipped%20course%20project/run.bat) or run from PowerShell:
```powershell
.\run.bat
```
* Compiles all source files.
* Boots the embedded HTTP server at `http://localhost:8080`.
* Automatically launches the interactive trading terminal in your default browser.

#### Option B: Maven Command Line
```bash
# Compile and package
mvn clean compile

# Launch the embedded ExchangeServer
mvn exec:java
```
Navigate to:
```
http://localhost:8080
```

#### Option C: Color-Coded Console Terminal
To run the interactive ANSI command-line trading client:
```bash
mvn exec:java -Dexec.mainClass="com.marketpulse.ui.ConsoleApp"
```

---

## 8. Automated Testing & Verification

The project includes 24 automated unit, integration, and stress tests organized into 5 specialized test suites:

```bash
# Execute all automated test suites
mvn test
```

### Test Suite Breakdown:

| Test Suite | Tests | Key Cases Verified | Invariants & Assertions |
| :--- | :---: | :--- | :--- |
| **`ConcurrencyTest`** | 2 | `testConcurrentOrderMatchingConservation`<br/>`testUnsynchronizedFailureSimulation` | 16 parallel threads $\times$ 200 orders (3,200 total); confirms $\Sigma\text{Bought} \equiv \Sigma\text{Sold}$ with 0 lost shares; proves race condition failures without locks. |
| **`AdvancedOrderTest`** | 6 | `testIcebergReplenishment`<br/>`testIcebergQueuePriorityLoss`<br/>`testStopLossTrigger`<br/>`testFillOrKillAbortsOnInsufficientDepth`<br/>`testImmediateOrCancelPartialFill`<br/>`testMicrostructureCalculations` | Iceberg slice reload & queue priority yield; conditional Stop-Loss conversion; FOK/IOC partial-fill and abort semantics; Stoikov Micro-price & OBI mathematical accuracy. |
| **`OrderBookTest`** | 8 | `testExactMatch`<br/>`testPartialFill`<br/>`testPricePriority`<br/>`testTimePriorityFIFO`<br/>`testMarketOrder`<br/>`testSelfTradePrevention`<br/>`testValidationFailure`<br/>`testInsufficientFunds` | Strict Price-Time FIFO matching; pre-match Cancel-Resting Self-Trade Prevention (wash-trade defense); numeric validation & insufficient balance exceptions. |
| **`PersistenceTest`** | 3 | `testAtomicTradeCommit`<br/>`testAtomicTradeRollbackOnFailure`<br/>`testAnalyticalQueries` | ACID transaction commit; 100% atomic rollback upon simulated database fault; JPQL analytical VWAP and leaderboard aggregations. |
| **`TradingWorkflowTest`** | 5 | `testMarginReservationOnLimitBuy`<br/>`testMarginReleaseOnCancellation`<br/>`testMultiTierBookSweepAndInventoryValidation`<br/>`testPriceImprovementCollateralRefund` | Pre-trade collateral reservations; margin unlock upon cancellation; multi-level order book sweep; buyer price-improvement cash refund. |
| **Total** | **24** | **100% Passing (0 Failures, 0 Errors, 0 Skipped)** | **BUILD SUCCESS** |

---

## 9. Repository Structure

```
.
├── pom.xml                               # Maven build configuration (Java 24, H2, Hibernate, Surefire)
├── schema.sql                            # Relational DDL (traders, orders, trades, indexes)
├── run.bat                               # Single-click Windows bootstrap launcher
├── statement.md                          # Problem statement, scope, target users & features
├── memory.md                             # Architectural log & system memory
├── assets/
│   └── screenshots/                      # Real terminal & benchmark execution captures
│       ├── terminal_overview.png
│       ├── candlestick_order_ticket.png
│       ├── academic_defense_modal.png
│       ├── hotkey_matrix_modal.png
│       └── terminal_tests_run.png
├── src/
│   ├── main/java/com/marketpulse/
│   │   ├── audit/                        # Append-only CSV logger & deterministic replay engine
│   │   ├── concurrency/                  # Multi-threaded simulation runners & trader tasks
│   │   ├── engine/                       # MatchingEngine singleton & per-symbol OrderBook
│   │   ├── exceptions/                   # Checked domain exceptions (InsufficientFunds, InvalidOrder)
│   │   ├── model/                        # Domain entities (Order, BuyOrder, SellOrder, Trader, Trade)
│   │   ├── persistence/
│   │   │   ├── DatabaseConfig.java       # In-memory H2 configuration & connection pooling
│   │   │   ├── jdbc/                     # Phase 1: Fast transactional DAOs (TradeDAO, OrderDAO)
│   │   │   └── jpa/                      # Phase 2: Hibernate JPA entities & AnalyticsRepository
│   │   ├── server/                       # ExchangeServer (Java 24 Virtual-Thread HTTP server)
│   │   └── ui/                           # ConsoleApp color-coded ANSI terminal
│   └── test/java/com/marketpulse/        # 5 automated JUnit test suites (24 tests)
└── web/
    ├── index.html                        # Glassmorphic terminal UI, Canvas engine, Defense Inspector
    └── app.js                            # ES6 reactive controller, audio chimes, replay slider
```

---

## 10. Authors & Academic Credentials

* **Student:** Mansi Kumari
* **Registration Number:** `25BAI11449`
* **Program:** B.Tech Computer Science & Engineering (Specialization in Artificial Intelligence & Machine Learning)
* **Institution:**  VIT Bhopal University
* **Course:** `CSE2006 — Programming in Java` 
