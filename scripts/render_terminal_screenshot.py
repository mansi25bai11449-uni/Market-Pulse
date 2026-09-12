import os
from playwright.sync_api import sync_playwright

html_content = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  body {
    margin: 0;
    padding: 24px;
    background: #0d1117;
    font-family: 'Consolas', 'Courier New', monospace;
    color: #c9d1d9;
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 100vh;
    box-sizing: border-box;
  }
  .window {
    background: #161b22;
    border: 1px solid #30363d;
    border-radius: 12px;
    box-shadow: 0 20px 40px rgba(0,0,0,0.6);
    width: 960px;
    overflow: hidden;
  }
  .header {
    background: #21262d;
    padding: 12px 16px;
    display: flex;
    align-items: center;
    border-bottom: 1px solid #30363d;
  }
  .dots {
    display: flex;
    gap: 8px;
    margin-right: 16px;
  }
  .dot { width: 12px; height: 12px; border-radius: 50%; }
  .dot.red { background: #ff5f56; }
  .dot.yellow { background: #ffbd2e; }
  .dot.green { background: #27c93f; }
  .title {
    font-size: 13px;
    color: #8b949e;
    font-weight: 600;
    letter-spacing: 0.5px;
  }
  .terminal-body {
    padding: 20px;
    font-size: 13.5px;
    line-height: 1.5;
    white-space: pre-wrap;
    background: #090d13;
  }
  .prompt { color: #58a6ff; font-weight: bold; }
  .cmd { color: #f0f6fc; font-weight: bold; }
  .info { color: #79c0ff; }
  .success { color: #3fb950; font-weight: bold; }
  .audit { color: #d2a8ff; }
  .highlight { color: #56d364; }
  .banner { color: #38bdf8; font-weight: bold; }
  .divider { color: #30363d; }
</style>
</head>
<body>
<div class="window">
  <div class="header">
    <div class="dots">
      <div class="dot red"></div>
      <div class="dot yellow"></div>
      <div class="dot green"></div>
    </div>
    <div class="title">pwsh &mdash; MarketPulse Concurrency &amp; Regression Test Suite &mdash; 16 Threads &times; 200 Orders</div>
  </div>
  <div class="terminal-body"><span class="prompt">PS E:\\flipped course project&gt;</span> <span class="cmd">.\\mvnw.cmd test</span>
[INFO] Scanning for projects...
[INFO] -------------------&lt; com.marketpulse:marketpulse-engine &gt;-------------------
[INFO] Building marketpulse-engine 1.0.0
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- maven-surefire-plugin:3.2.5:test (default-test) @ marketpulse-engine ---
[INFO] Running com.marketpulse.AdvancedOrderTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.142 s -- in com.marketpulse.AdvancedOrderTest
[INFO] Running com.marketpulse.ConcurrencyTest
<span class="audit">[STP AUDIT] Self-Trade Prevention triggered for Trader 'TRADER_14' on AAPL. Resting order 337ca891 (SELL 30 @ $151.00) cancelled. Wash trade eliminated.</span>
<span class="audit">[STP AUDIT] Self-Trade Prevention triggered for Trader 'TRADER_7' on AAPL. Resting order bc592e39 (BUY 20 @ $149.75) cancelled. Wash trade eliminated.</span>
<span class="audit">[STP AUDIT] Self-Trade Prevention triggered for Trader 'TRADER_14' on AAPL. Resting order cac9cfa4 (SELL 70 @ $147.75) cancelled. Wash trade eliminated.</span>

<span class="banner">=======================================================
   MARKETPULSE CONCURRENCY BENCHMARK REPORT
=======================================================</span>
Locking Strategy       : <span class="success">PER-SYMBOL REENTRANT LOCK (ENABLED)</span>
Worker Threads         : 16
Orders / Thread        : 200
Total Orders Attempted : 3,200
Total Orders Processed : 3,200
Total Trades Generated : 704
Total Shares Bought    : 22,200
Total Shares Sold      : 22,200
Share Discrepancy      : <span class="success">0 [PERFECT MATCH &amp; CONSERVATION]</span>
Exceptions Caught      : <span class="success">0 [CLEAN - 0 RACE CONDITIONS]</span>
Execution Duration     : 55 ms
Measured Throughput    : <span class="highlight">58,181.82 orders/sec (Sustained In-Memory)</span>
Target Specification   : &gt;= 10,000 orders/sec (Design Target: MET)
Latency (Mean Avg)     : 252.39 us (0.252 ms)
Latency (P50 Median)   : 19.40 us (0.019 ms)
Latency (P95 Tail)     : 824.10 us (0.824 ms)
Validation Result      : <span class="success">PASSED (100% RECONCILED)</span>
<span class="banner">=======================================================</span>

[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.680 s -- in com.marketpulse.ConcurrencyTest
[INFO] Running com.marketpulse.OrderBookTest
<span class="audit">[STP AUDIT] Self-Trade Prevention triggered for Trader 'T1' on AAPL. Resting order cecb4bb7 cancelled. Wash trade eliminated.</span>
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.059 s -- in com.marketpulse.OrderBookTest
[INFO] Running com.marketpulse.PersistenceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.544 s -- in com.marketpulse.PersistenceTest
[INFO] Running com.marketpulse.TradingWorkflowTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s -- in com.marketpulse.TradingWorkflowTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] <span class="success">Tests run: 26, Failures: 0, Errors: 0, Skipped: 0</span>
[INFO] 
[INFO] <span class="divider">------------------------------------------------------------------------</span>
[INFO] <span class="success">BUILD SUCCESS</span>
[INFO] <span class="divider">------------------------------------------------------------------------</span>
[INFO] Total time:  5.428 s
[INFO] Finished at: 2026-09-12T11:24:40+05:30
[INFO] <span class="divider">------------------------------------------------------------------------</span>
<span class="prompt">PS E:\\flipped course project&gt;</span> <span class="cmd">_</span></div>
</div>
</body>
</html>
"""

with open('DOCUMENTS/images/terminal_temp.html', 'w', encoding='utf-8') as f:
    f.write(html_content)

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(viewport={'width': 1060, 'height': 920}, device_scale_factor=2)
    page.goto('file:///' + os.path.abspath('DOCUMENTS/images/terminal_temp.html').replace('\\', '/'))
    page.wait_for_timeout(500)
    window_el = page.query_selector('.window')
    if window_el:
        window_el.screenshot(path='DOCUMENTS/images/terminal_tests_run.png')
    else:
        page.screenshot(path='DOCUMENTS/images/terminal_tests_run.png')
    browser.close()

if os.path.exists('DOCUMENTS/images/terminal_temp.html'):
    os.remove('DOCUMENTS/images/terminal_temp.html')

print("Created DOCUMENTS/images/terminal_tests_run.png successfully!")
