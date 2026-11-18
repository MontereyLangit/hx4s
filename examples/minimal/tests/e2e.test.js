import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import path from 'node:path';
import test from 'node:test';
import { root } from './support.js';

const port = Number(process.env.HX4S_E2E_PORT ?? 8791);
const origin = `http://127.0.0.1:${port}`;

/** Serves the build output with wrangler dev (local workerd) */
function startWrangler() {
  const wrangler = path.join(root, 'node_modules', 'wrangler', 'bin', 'wrangler.js');
  const child = spawn(process.execPath, [wrangler, 'dev', '--ip', '127.0.0.1', '--port', String(port), '--show-interactive-dev-session=false'], {
    cwd: root,
    env: { ...process.env, WRANGLER_SEND_METRICS: 'false', CI: '1' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let log = '';
  child.stdout.on('data', (chunk) => { log += chunk; });
  child.stderr.on('data', (chunk) => { log += chunk; });
  return { child, log: () => log };
}

function stop(child) {
  if (child.exitCode !== null) return;
  // wrangler starts workerd as a child process, so stop the whole process tree
  if (process.platform === 'win32') spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F']);
  else child.kill('SIGTERM');
}

async function waitForServer(server) {
  for (let i = 0; i < 240; i++) {
    if (server.child.exitCode !== null) break;
    try {
      const response = await fetch(`${origin}/`);
      if (response.ok) return;
    } catch {
      // Not started yet
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  assert.fail(`wrangler dev did not start:\n${server.log()}`);
}

test('opening the SSR page in a real browser and clicking islands changes state without hydration errors', async (t) => {
  if (process.env.HX4S_SKIP_E2E === '1') {
    t.skip('HX4S_SKIP_E2E=1');
    return;
  }
  const { chromium } = await import('playwright');
  const server = startWrangler();
  let browser;
  try {
    await waitForServer(server);
    // Both static files and the Worker respond on workerd
    assert.equal((await fetch(`${origin}/static/client.js`)).status, 200);
    assert.equal((await fetch(`${origin}/index.js`)).status, 404, 'the Worker code is not served statically');

    browser = await chromium.launch();
    const page = await browser.newPage();
    const problems = [];
    page.on('console', (message) => { if (message.type() === 'error' || message.type() === 'warning') problems.push(`${message.type()}: ${message.text()}`); });
    page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`));

    await page.goto(`${origin}/`);
    await page.waitForSelector('html[data-hx4s-ready]');
    const counters = page.locator('button.counter');
    await counters.nth(0).click();
    await counters.nth(0).click();
    await counters.nth(1).click();
    await assert.doesNotReject(page.waitForFunction(() =>
      [...document.querySelectorAll('button.counter')].map((b) => b.textContent).join('|') === 'A: 2|B: 11'));
    await page.locator('.item-list li button').nth(2).click();
    await page.waitForFunction(() => document.querySelector('.selected-name')?.textContent === 'Selected: grape😀');

    await page.goto(`${origin}/items/2`);
    assert.equal(await page.locator('.item-name').textContent(), '</script><b>tag</b> "quoted" \'single\'');
    await page.fill('input[name="name"]', 'Saved from the browser 🌐');
    await page.click('button[type="submit"]');
    await page.waitForSelector('.saved');
    assert.equal(await page.locator('.saved').textContent(), 'Saved: Saved from the browser 🌐');

    assert.deepEqual(problems, [], 'no console errors or warnings (hydration mismatches are reported there)');
  } finally {
    await browser?.close();
    stop(server.child);
  }
});
