#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const os = require('os');
const { spawn } = require('child_process');

const CHROME_BIN =
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const DEBUG_PORT = 9222;
const DEFAULT_URL = 'http://localhost:4200/en/calculator/basic';
const diagnosticContext = {
  consoleEvents: [],
  networkEvents: [],
  partialStates: {},
};

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchJson(url, retries = 40) {
  let lastError = null;
  for (let i = 0; i < retries; i += 1) {
    try {
      const res = await fetch(url);
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      return await res.json();
    } catch (error) {
      lastError = error;
      await sleep(250);
    }
  }
  throw lastError;
}

async function fetchPageTarget(debugPort) {
  const targets = await fetchJson(`http://127.0.0.1:${debugPort}/json/list`);
  const pageTarget = targets.find((target) => target.type === 'page');
  if (!pageTarget?.webSocketDebuggerUrl) {
    throw new Error('No page target available');
  }
  return pageTarget;
}

class CdpClient {
  constructor(wsUrl) {
    this.ws = new WebSocket(wsUrl);
    this.id = 0;
    this.pending = new Map();
    this.listeners = new Map();
  }

  async connect() {
    await new Promise((resolve, reject) => {
      this.ws.addEventListener('open', resolve, { once: true });
      this.ws.addEventListener('error', reject, { once: true });
    });

    this.ws.addEventListener('message', (event) => {
      const message = JSON.parse(String(event.data));
      if (message.id) {
        const pending = this.pending.get(message.id);
        if (!pending) return;
        this.pending.delete(message.id);
        if (message.error) {
          pending.reject(new Error(message.error.message));
        } else {
          pending.resolve(message.result);
        }
        return;
      }

      const bucket = this.listeners.get(message.method);
      if (!bucket) return;
      for (const listener of bucket) {
        listener(message.params);
      }
    });
  }

  on(method, listener) {
    const bucket = this.listeners.get(method) || [];
    bucket.push(listener);
    this.listeners.set(method, bucket);
  }

  send(method, params = {}) {
    const id = ++this.id;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
    });
  }

  async evaluate(expression) {
    const result = await this.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true,
    });
    return result.result?.value;
  }

  async close() {
    if (this.ws.readyState === WebSocket.OPEN) {
      this.ws.close();
    }
  }
}

async function waitFor(client, label, expression, timeoutMs = 15000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const value = await client.evaluate(expression);
    if (value) {
      return value;
    }
    await sleep(200);
  }
  throw new Error(`Timeout waiting for ${label}`);
}

async function captureState(client, label) {
  await waitFor(
    client,
    `${label} preview settled`,
    `(() => !document.querySelector('.loading-overlay'))()`,
    15000,
  );

  const state = await client.evaluate(`(async () => {
    const fileCards = [...document.querySelectorAll('.file-card')].map((card, index) => ({
      index,
      name: card.querySelector('.file-name')?.textContent?.trim() || null,
      active: card.classList.contains('active'),
    }));
    const warning =
      [...document.querySelectorAll('app-alert, .step-warning, .error-overlay')]
        .map((el) => el.textContent?.trim() || '')
        .filter(Boolean);
    const uploadFormEl = document.querySelector('app-upload-form');
    const uploadCmp = globalThis.ng?.getComponent?.(uploadFormEl) || null;
    const items = uploadCmp?.items?.()
      ?.map?.((item, index) => ({
        index,
        fileName: item.file?.name || null,
        fileSize: item.file?.size ?? null,
        previewName: item.previewFile?.name || null,
        previewSize: item.previewFile?.size ?? null,
        selected: item.file === uploadCmp.selectedFile?.(),
      })) || [];
    let selectedPreviewAnalysis = null;
    const previewFile = uploadCmp?.getSelectedPreviewFile?.() || null;
    if (previewFile?.arrayBuffer) {
      const buffer = await previewFile.arrayBuffer();
      const byteLength = buffer.byteLength;
      const bytes = [...new Uint8Array(buffer.slice(0, Math.min(byteLength, 16)))];
      let validation = 'unsupported_payload';
      let faceCount = null;
      let expectedSize = null;
      if (byteLength > 0) {
        if (byteLength >= 84) {
          faceCount = new DataView(buffer).getUint32(80, true);
          expectedSize = 84 + faceCount * 50;
          if (expectedSize === byteLength) {
            validation = 'binary';
          }
        }
        if (validation === 'unsupported_payload') {
          const sampleBytes = new Uint8Array(buffer, 0, Math.min(byteLength, 2048));
          let printable = 0;
          for (const value of sampleBytes) {
            const isWhitespace = value === 9 || value === 10 || value === 13 || value === 32;
            const isPrintableAscii = value >= 32 && value <= 126;
            if (isWhitespace || isPrintableAscii) printable += 1;
          }
          if (sampleBytes.length > 0 && printable / sampleBytes.length >= 0.98) {
            const sample = new TextDecoder().decode(sampleBytes).replace(/\\0/g, '');
            const normalized = sample.trimStart().toLowerCase();
            if (normalized.startsWith('solid') && normalized.includes('facet')) {
              validation = 'ascii';
            }
          }
        }
      } else {
        validation = 'empty';
      }
      selectedPreviewAnalysis = {
        name: previewFile.name || null,
        size: previewFile.size ?? null,
        byteLength,
        firstBytes: bytes,
        faceCount,
        expectedSize,
        validation,
      };
    }
    const session = new URL(location.href).searchParams.get('session');
    let browserFetchSizes = [];
    if (session) {
      try {
        const response = await fetch(\`http://localhost:8000/api/quote-sessions/\${session}\`);
        const sessionJson = await response.json();
        browserFetchSizes = await Promise.all(
          (sessionJson.items || []).map(async (item) => {
            const blob = await fetch(
              \`http://localhost:8000/api/quote-sessions/\${session}/line-items/\${item.id}/content\`,
            ).then((res) => res.blob());
            return {
              id: item.id,
              fileName: item.originalFilename,
              blobSize: blob.size,
            };
          }),
        );
      } catch (error) {
        browserFetchSizes = [{ error: String(error) }];
      }
    }
    return {
      href: location.href,
      activeFile: document.querySelector('.file-card.active .file-name')?.textContent?.trim() || null,
      fileCards,
      dims: document.querySelector('.viewer-container .dims-overlay')?.textContent?.trim() || null,
      unavailable: document.querySelector('.viewer-container .error-overlay span')?.textContent?.trim() || null,
      warning,
      items,
      selectedPreviewAnalysis,
      browserFetchSizes,
      session,
    };
  })()`);

  const screenshot = await client.send('Page.captureScreenshot', {
    format: 'png',
    fromSurface: true,
  });
  const screenshotPath = path.join(
    os.tmpdir(),
    `printcalc-${label.replace(/[^a-z0-9_-]+/gi, '_')}.png`,
  );
  fs.writeFileSync(screenshotPath, Buffer.from(screenshot.data, 'base64'));

  return { ...state, screenshotPath };
}

async function clickCard(client, index) {
  const ok = await client.evaluate(`(() => {
    const card = document.querySelectorAll('.file-card')[${index}];
    if (!card) return false;
    card.click();
    return true;
  })()`);
  if (!ok) {
    throw new Error(`Unable to click file card at index ${index}`);
  }
  await sleep(300);
}

async function clickCalculate(client) {
  const clicked = await client.evaluate(`(() => {
    const buttons = [...document.querySelectorAll('button')];
    const target = buttons.find((button) =>
      /calculate quote|calcola/i.test((button.textContent || '').trim())
    );
    if (!target) return false;
    target.click();
    return true;
  })()`);
  if (!clicked) {
    throw new Error('Calculate button not found');
  }
}

async function setFiles(client, files) {
  const { root } = await client.send('DOM.getDocument', { depth: -1, pierce: true });
  const { nodeId } = await client.send('DOM.querySelector', {
    nodeId: root.nodeId,
    selector: 'input[type="file"]',
  });

  if (!nodeId) {
    throw new Error('File input not found');
  }

  await client.send('DOM.setFileInputFiles', {
    nodeId,
    files,
  });
}

async function main() {
  const files = process.argv.slice(2);
  if (files.length < 2) {
    console.error('Usage: node scripts/browser-repro.js <file1.stl> <file2.stl>');
    process.exit(1);
  }

  for (const file of files) {
    if (!fs.existsSync(file)) {
      throw new Error(`File not found: ${file}`);
    }
  }

  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'printcalc-chrome-'));
  const chrome = spawn(
    CHROME_BIN,
    [
      '--headless=new',
      '--disable-gpu',
      '--enable-webgl',
      '--ignore-gpu-blocklist',
      '--use-gl=angle',
      '--use-angle=swiftshader',
      '--enable-unsafe-swiftshader',
      '--no-first-run',
      '--no-default-browser-check',
      `--user-data-dir=${userDataDir}`,
      `--remote-debugging-port=${DEBUG_PORT}`,
      'about:blank',
    ],
    { stdio: 'ignore' },
  );

  let client;
  try {
    const pageTarget = await fetchPageTarget(DEBUG_PORT);
    client = new CdpClient(pageTarget.webSocketDebuggerUrl);
    await client.connect();

    const networkEvents = [];
    client.on('Runtime.consoleAPICalled', (params) => {
      diagnosticContext.consoleEvents.push({
        type: params.type,
        text: (params.args || []).map((arg) => arg.value).join(' '),
      });
    });
    client.on('Network.requestWillBeSent', (params) => {
      if (String(params.request?.url || '').includes('/api/quote-sessions')) {
        diagnosticContext.networkEvents.push({
          phase: 'request',
          method: params.request.method,
          url: params.request.url,
        });
      }
    });
    client.on('Network.responseReceived', (params) => {
      if (String(params.response?.url || '').includes('/api/quote-sessions')) {
        diagnosticContext.networkEvents.push({
          phase: 'response',
          status: params.response.status,
          url: params.response.url,
        });
      }
    });

    await client.send('Page.enable');
    await client.send('Runtime.enable');
    await client.send('DOM.enable');
    await client.send('Network.enable');

    await client.send('Page.navigate', { url: DEFAULT_URL });
    await waitFor(
      client,
      'calculator page',
      `(() => !!document.querySelector('input[type="file"]'))()`,
      20000,
    );

    await setFiles(client, files);
    await waitFor(
      client,
      'uploaded files',
      `(() => document.querySelectorAll('.file-card').length >= ${files.length})()`,
      15000,
    );

    const afterUpload = await captureState(client, 'after-upload');
    diagnosticContext.partialStates.afterUpload = afterUpload;
    await clickCalculate(client);
    await waitFor(
      client,
      'session id after calculate',
      `(() => new URL(location.href).searchParams.get('session'))()`,
      90000,
    );

    await sleep(2500);
    const afterCalculate = await captureState(client, 'after-calculate');
    diagnosticContext.partialStates.afterCalculate = afterCalculate;

    const perCardBeforeReload = [];
    for (let i = 0; i < afterCalculate.fileCards.length; i += 1) {
      await clickCard(client, i);
      perCardBeforeReload.push(
        await captureState(client, `before-reload-card-${i}`),
      );
    }

    await client.send('Page.reload', { ignoreCache: true });
    await waitFor(
      client,
      'restored files after reload',
      `(() => document.querySelectorAll('.file-card').length >= ${files.length})()`,
      30000,
    );
    await sleep(2500);
    const afterReload = await captureState(client, 'after-reload');
    diagnosticContext.partialStates.afterReload = afterReload;

    const perCardAfterReload = [];
    for (let i = 0; i < afterReload.fileCards.length; i += 1) {
      await clickCard(client, i);
      perCardAfterReload.push(
        await captureState(client, `after-reload-card-${i}`),
      );
    }

    console.log(
      JSON.stringify(
        {
          files,
          afterUpload,
          afterCalculate,
          perCardBeforeReload,
          afterReload,
          perCardAfterReload,
          consoleEvents: diagnosticContext.consoleEvents,
          networkEvents: diagnosticContext.networkEvents,
        },
        null,
        2,
      ),
    );
  } finally {
    if (client) {
      await client.close();
    }
    chrome.kill('SIGKILL');
  }
}

main().catch((error) => {
  console.error(error);
  console.error(JSON.stringify(diagnosticContext, null, 2));
  process.exit(1);
});
