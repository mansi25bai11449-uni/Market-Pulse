const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const STITCH_KEY = process.env.STITCH_API_KEY || '';

function runStitch() {
  return new Promise((resolve, reject) => {
    console.log('[Stitch] Launching mcp-remote proxy...');
    const child = spawn('npx', [
      '-y',
      'mcp-remote',
      'https://stitch.googleapis.com/mcp',
      '--header',
      `X-Goog-Api-Key:${STITCH_KEY}`
    ], {
      shell: true,
      stdio: ['pipe', 'pipe', 'pipe']
    });

    let stdoutBuffer = '';
    let reqId = 1;
    let pendingRequests = new Map();
    let isReady = false;

    function sendRequest(method, params) {
      return new Promise((res, rej) => {
        const id = reqId++;
        pendingRequests.set(id, { resolve: res, reject: rej });
        const payload = JSON.stringify({ jsonrpc: '2.0', id, method, params });
        console.log(`[Stitch Client -> MCP] Sending ${method} (id=${id})...`);
        child.stdin.write(payload + '\n');
      });
    }

    child.stdout.on('data', (data) => {
      stdoutBuffer += data.toString();
      const lines = stdoutBuffer.split('\n');
      stdoutBuffer = lines.pop();
      for (const line of lines) {
        if (!line.trim()) continue;
        try {
          const msg = JSON.parse(line.trim());
          if (msg.id && pendingRequests.has(msg.id)) {
            const { resolve } = pendingRequests.get(msg.id);
            pendingRequests.delete(msg.id);
            resolve(msg);
          }
        } catch (e) {
          // not JSON line
        }
      }
    });

    child.stderr.on('data', async (data) => {
      const text = data.toString();
      if (text.includes('Proxy established successfully') && !isReady) {
        isReady = true;
        console.log('[Stitch] Proxy connected! Initializing session...');
        try {
          // 1. Initialize
          await sendRequest('initialize', {
            protocolVersion: '2024-11-05',
            capabilities: {},
            clientInfo: { name: 'marketpulse-agent', version: '1.0.0' }
          });
          console.log('[Stitch] Session initialized.');

          // 2. Create Project
          console.log('[Stitch] Creating Project: MarketPulse Exchange Terminal...');
          const createProjRes = await sendRequest('tools/call', {
            name: 'create_project',
            arguments: {
              title: 'MarketPulse High-Frequency Exchange Terminal'
            }
          });
          console.log('[Stitch] Create Project Response:', JSON.stringify(createProjRes, null, 2).slice(0, 500));

          let projectId = '';
          if (createProjRes.result && createProjRes.result.content) {
            for (const c of createProjRes.result.content) {
              try {
                const parsed = JSON.parse(c.text);
                if (parsed.projectId) projectId = parsed.projectId;
                if (parsed.name && !projectId) projectId = parsed.name.replace('projects/', '');
              } catch (e) {}
            }
          }

          if (!projectId) {
            console.log('[Stitch] Listing projects to find ID...');
            const listRes = await sendRequest('tools/call', {
              name: 'list_projects',
              arguments: {}
            });
            console.log('[Stitch] List Projects:', JSON.stringify(listRes, null, 2).slice(0, 500));
            // Extract latest project id
            const text = JSON.stringify(listRes);
            const match = text.match(/projects\/([0-9]+)/);
            if (match) projectId = match[1];
          }

          console.log(`[Stitch] Target Project ID: ${projectId}`);

          // 3. Generate Screen with Fine-Grained Prompt
          const prompt = `
High-Frequency Stock Exchange Trading Terminal & Concurrency Matching Engine Dashboard called "MarketPulse".

Key Sections:
1. Header Bar:
   - System Logo "MarketPulse Engine v1.0-RC" with glowing emerald live status indicator ("ENGINE ACTIVE | 0.12ms LATENCY").
   - Ticker Strip for AAPL ($182.40 +1.25%), TSLA ($245.10 -0.80%), NVDA ($890.50 +3.40%) with mini sparklines and 24h volume.
   - Connected Trader profile chip ("Trader #1 - Alice ($50,000 Cash)").

2. Left Column (Order Entry & Portfolio):
   - Fast Order Placement Form with tabs for BUY (emerald accent) and SELL (crimson accent).
   - Order Type Switcher: LIMIT vs MARKET.
   - Price Input with +/- $0.05 step buttons and quick bid/ask matching buttons.
   - Quantity Input with slider and quick-select pills (25%, 50%, 75%, 100% of buying power).
   - Place Order CTA button with animated glowing hover state.
   - Mini Trader Portfolio Card showing Cash Balance, Stock Holdings, and Open Pending Orders.

3. Middle Column (Order Book Ladder & Depth Chart):
   - Real-Time Order Book Ladder:
     - ASKS section: descending red rows showing Price, Quantity, Cumulative Depth bar.
     - SPREAD bar: highlighting current spread ($0.05 / 0.03%) with mid-market price $182.40.
     - BIDS section: ascending emerald rows showing Price, Quantity, Cumulative Depth bar.
   - Depth Chart Visualizer: Visual bid/ask cumulative volume curve meeting at the market spread.

4. Right Column (Concurrency Benchmark Bench & Trade Feed):
   - Concurrency Stress Test Control Bench:
     - Dials and controls for: Number of Concurrent Trader Threads (1 to 50 threads), Order Count (100 to 5,000 orders).
     - Execution Mode toggle: "Per-Symbol ReentrantLock (100% Reconciled)" vs "Locking Disabled (Race Condition Simulator)".
     - Empirical Reconciliation Gauge: Total Buy Shares vs Total Sell Shares counter with "ZERO SHARES LOST" validation badge.
   - Live Executed Trades Stream: Streaming trade tickets with Trade ID, Timestamp, Counterparties (Buyer/Seller), Price, Quantity, and Execution Status.

5. Bottom Bar (Audit & Replay Controls):
   - Deterministic Replay Player with timeline scrubber, Play/Pause, Step Next Order button, and Replay Speed (1x, 5x, 10x).
   - JPQL Analytical Reports Button opening modal with Most Active Stock, Trader Wealth Leaderboard, and Historical Volatility.

Visual Style:
- Cyber-Industrial Dark Glassmorphism.
- Background: Obsidian black (#080C14) with subtle glowing hex-grid texture and translucent blurred glass cards (backdrop-filter: blur(16px), border: 1px solid rgba(255,255,255,0.08)).
- Palette: Neon Cyan / Emerald (#00F5D4) for Bids, Vivid Crimson (#FF3366) for Asks, Electric Amber (#FFB800) for Spread/Replay, Purple/Violet (#7928CA) for Concurrency Bench.
- Typography: High-contrast monospace numerical figures for instant readability.
- Layout: Desktop 1440px multi-column command center.
`;

          console.log('[Stitch] Generating Screen with fine-grained prompt...');
          const genRes = await sendRequest('tools/call', {
            name: 'generate_screen_from_text',
            arguments: {
              projectId: projectId,
              prompt: prompt,
              deviceType: 'DESKTOP'
            }
          });
          console.log('[Stitch] Generate Screen Result:', JSON.stringify(genRes, null, 2).slice(0, 1000));

          // Save response artifact
          const stitchDir = path.join(__dirname, '..', '.stitch', 'designs');
          fs.mkdirSync(stitchDir, { recursive: true });
          fs.writeFileSync(path.join(stitchDir, 'stitch_raw_output.json'), JSON.stringify(genRes, null, 2));

          console.log('[Stitch] Saved raw output to .stitch/designs/stitch_raw_output.json');
          child.kill();
          resolve(genRes);
        } catch (err) {
          console.error('[Stitch] Error during execution:', err);
          child.kill();
          reject(err);
        }
      }
    });

    setTimeout(() => {
      console.log('[Stitch] Timeout reached (60s). Exiting.');
      child.kill();
      resolve(null);
    }, 60000);
  });
}

runStitch().catch(console.error);
