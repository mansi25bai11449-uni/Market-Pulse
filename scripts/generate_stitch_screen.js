const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const STITCH_KEY = process.env.STITCH_API_KEY || '';
const PROJECT_ID = '17382379628497271953';

async function generate() {
  console.log('[Stitch Generator] Starting...');
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
  let isReady = false;

  child.stdout.on('data', (data) => {
    stdoutBuffer += data.toString();
    const lines = stdoutBuffer.split('\n');
    stdoutBuffer = lines.pop();
    for (const line of lines) {
      if (!line.trim()) continue;
      try {
        const msg = JSON.parse(line.trim());
        if (msg.id === 2) {
          console.log('[Stitch Generator] Screen generated successfully!');
          console.log(JSON.stringify(msg, null, 2).slice(0, 1000));
          const stitchDir = path.join(__dirname, '..', '.stitch', 'designs');
          fs.mkdirSync(stitchDir, { recursive: true });
          fs.writeFileSync(path.join(stitchDir, 'screen_generated.json'), JSON.stringify(msg, null, 2));
          child.kill();
          process.exit(0);
        }
      } catch (e) {}
    }
  });

  child.stderr.on('data', (data) => {
    const text = data.toString();
    if (text.includes('Proxy established successfully') && !isReady) {
      isReady = true;
      console.log('[Stitch Generator] Proxy established. Sending init...');
      child.stdin.write(JSON.stringify({
        jsonrpc: '2.0',
        id: 1,
        method: 'initialize',
        params: { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'cli', version: '1.0' } }
      }) + '\n');

      setTimeout(() => {
        console.log('[Stitch Generator] Sending generate_screen_from_text call...');
        const prompt = 'MarketPulse High-Frequency Trading Terminal & Concurrency Matching Engine Dashboard with dark obsidian glassmorphic theme (#080C14). Features: top ticker strip, left-hand BUY/SELL order entry panel with price/quantity inputs, central live order book ladder with bids and asks depth visualizer, right-hand concurrent stress test bench with reconciliation gauges (Total Buy Shares vs Total Sell Shares), and live trade stream at the bottom.';
        child.stdin.write(JSON.stringify({
          jsonrpc: '2.0',
          id: 2,
          method: 'tools/call',
          params: {
            name: 'generate_screen_from_text',
            arguments: {
              projectId: PROJECT_ID,
              prompt: prompt,
              deviceType: 'DESKTOP'
            }
          }
        }) + '\n');
      }, 2500);
    }
  });

  setTimeout(() => {
    console.log('[Stitch Generator] Timeout 120s reached.');
    child.kill();
    process.exit(0);
  }, 120000);
}

generate();
