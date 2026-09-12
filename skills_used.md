# Skills Used in MarketPulse Development

This document catalogs every engineering, financial, and design skill leveraged throughout the construction of the **MarketPulse Concurrent Stock Order Matching Engine** and **Exchange Terminal**.

---

## 1. `stitch-ui-design`
* **Purpose:** Crafting fine-grained, context-rich prompts for Google Stitch AI to generate high-fidelity financial UI screens.
* **Key Principles Applied:**
  * Specificity in component anatomy (bids/asks depth ladder, ticker tape, stress test dial, reconciliation counter).
  * Explicit aesthetic definition (Obsidian dark mode, `#00F5D4` cyan/emerald bid accents, `#FF3366` crimson ask accents, high-contrast monospace numericals).
  * Responsive layout structuring (desktop 1440px trading terminal layout).

---

## 2. `stitch-loop`
* **Purpose:** Autonomous loop pattern for interacting with Stitch MCP tools over stdio/JSON-RPC.
* **Key Principles Applied:**
  * Programmatic project creation via `create_project`.
  * Execution of `generate_screen_from_text` using fine-grained trading terminal prompts.
  * Retrieval and staging of Stitch output assets in `.stitch/designs/`.

---

## 3. `frontend-design`
* **Purpose:** Creating a distinctive, memorable, and production-grade interface that avoids generic "AI template" tropes.
* **Aesthetic Direction:** *Cyber-Industrial High-Frequency Trading Terminal* (DFII Score: 14/15).
* **Key Principles Applied:**
  * Dominant color story: Deep obsidian `#0B0F17` with neon accents, custom glassmorphic cards, and zero generic framework defaults.
  * Spatial rhythm: Real-time order book ladder with live cumulative volume percentage fills.
  * Micro-animations: Kinetic glowing pulses on order fills, real-time ticker tape, smooth depth chart rendering.
  * Active Open Orders Panel: Real-time order status tracking with instantaneous cancellation and margin recovery.

---

## 4. `tdd-workflow` & `test-driven-development`
* **Purpose:** Rigorous verification of matching logic invariants.
* **Key Principles Applied:**
  * Unit tests for price-time priority, exact match, partial fills, order cancellation, and empty book behavior.
  * Stress testing under high thread concurrency asserting share conservation:
    $$\sum \text{Shares}_{\text{BUY}} = \sum \text{Shares}_{\text{SELL}}$$
  * Negative test cases verifying custom checked exceptions (`InvalidOrderException`, `InsufficientFundsException`).
  * End-to-end trading workflows testing margin reservation, margin release on cancellation, and multi-tier book sweeps.

---

## 5. `database-design` & `database`
* **Purpose:** Relational database schema definition, atomic transaction modeling, and dual-layer persistence (JDBC + JPA).
* **Key Principles Applied:**
  * Normalized schema for `traders`, `orders`, and `trades` with foreign key integrity.
  * Explicit JDBC transaction boundaries (`setAutoCommit(false)`, `commit()`, `rollback()`) to guarantee atomic cash balance transfers.
  * JPQL analytical queries for portfolio leaderboards, stock volume velocity, and price history.

---

## 6. `powershell-windows` & `windows-shell-reliability`
* **Purpose:** Robust execution in Windows PowerShell environments.
* **Key Principles Applied:**
  * Proper escaping of paths, parameters, and environment variables.
  * Detection and direct invocation of local OpenJDK 24 toolchains.
  * Portable Maven wrapper bootstrapping.

---

## 7. `webapp-testing` & Browser Subagent Automation
* **Purpose:** Automated UI and trade lifecycle validation directly against live browser DOM.
* **Key Principles Applied:**
  * Verification of live order placement, L2 book ladder updates, and active order cancellation.
  * Stress test bench execution with live reconciliation metrics.
  * Session video recording capturing end-to-end trader interactions.
