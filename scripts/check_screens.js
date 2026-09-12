const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const STITCH_KEY = process.env.STITCH_API_KEY || '';
const PROJECT_ID = '17382379628497271953';

function check() {
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
  let isReady = false;

  child.stdout.on('data', (data) => {
    stdoutBuffer += data.toString();
    const lines = stdoutBuffer.split('\n');
    stdoutBuffer = lines.pop();
    for (const line of lines) {
      if (!line.trim()) continue;
      try {
        const msg = JSON.parse(line.trim());
        console.log('MSG_RESPONSE:', JSON.stringify(msg, null, 2));
        if (msg.id === 2 && msg.result) {
          const stitchDir = path.join(__dirname, '..', '.stitch', 'designs');
          fs.mkdirSync(stitchDir, { recursive: true });
          fs.writeFileSync(path.join(stitchDir, 'screens_list.json'), JSON.stringify(msg.result, null, 2));
        }
      } catch (e) {}
    }
  });

  child.stderr.on('data', (data) => {
    const text = data.toString();
    if (text.includes('Proxy established successfully') && !isReady) {
      isReady = true;
      console.log('Connected! Sending init...');
      child.stdin.write(JSON.stringify({
        jsonrpc: '2.0',
        id: 1,
        method: 'initialize',
        params: { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'cli', version: '1.0' } }
      }) + '\n');

      setTimeout(() => {
        console.log(`Listing screens for project ${PROJECT_ID}...`);
        child.stdin.write(JSON.stringify({
          jsonrpc: '2.0',
          id: 2,
          method: 'tools/call',
          params: {
            name: 'list_screens',
            arguments: { projectId: PROJECT_ID }
          }
        }) + '\n');
      }, 2000);
    }
  });

  setTimeout(() => {
    child.kill();
    process.exit(0);
  }, 25000);
}

check();
