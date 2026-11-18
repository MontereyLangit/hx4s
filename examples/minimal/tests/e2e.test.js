import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import path from 'node:path';
import test from 'node:test';
import { brotliCompressSync, gzipSync } from 'node:zlib';
import { JSDOM } from 'jsdom';
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

    const scriptResponses = new Map();
    page.on('response', (response) => {
      if (new URL(response.url()).pathname.endsWith('.js')) scriptResponses.set(response.url(), response);
    });
    const scriptsFor = async (pathname) => {
      scriptResponses.clear();
      await page.goto(`${origin}${pathname}`);
      await page.waitForSelector('html[data-hx4s-ready]');
      return new Map(await Promise.all([...scriptResponses].map(async ([url, response]) => [url, await response.body()])));
    };
    // Routing disables the HTTP cache. Keep one kind of Island on each copy of the production SSR page.
    await page.route(new RegExp(`^${origin}/\\?(counter|item-list)-only$`), async (route) => {
      const response = await route.fetch();
      const dom = new JSDOM(await response.text());
      const keep = new URL(response.url()).search.slice(1).replace('-only', '');
      const islands = dom.window.document.querySelectorAll('honox-island');
      for (const island of islands) {
        if (island.getAttribute('component-name') !== `/app/islands/${keep}.tsx`) island.remove();
      }
      await route.fulfill({ response, body: dom.serialize() });
      dom.window.close();
    });
    const withoutIslands = await scriptsFor('/items/2');
    const countersOnly = await scriptsFor('/?counter-only');
    await page.locator('button.counter').first().click();
    await page.waitForFunction(() => document.querySelector('button.counter').textContent === 'A: 1');

    const itemsOnly = await scriptsFor('/?item-list-only');
    const allIslands = await scriptsFor('/');
    const implementations = (scripts) => ['CounterJs', 'Selected: '].filter((marker) =>
      [...scripts.values()].some((body) => body.includes(marker)));
    assert.deepEqual(implementations(withoutIslands), []);
    assert.deepEqual(implementations(countersOnly), ['CounterJs']);
    assert.deepEqual(implementations(itemsOnly), ['Selected: ']);
    assert.deepEqual(implementations(allIslands), ['CounterJs', 'Selected: ']);
    assert.ok([...countersOnly.keys()].some((url) => itemsOnly.has(url) && !withoutIslands.has(url)),
      'both Islands use a common chunk that the SSR-only page does not request');
    for (const compress of [gzipSync, brotliCompressSync]) {
      const bytes = (scripts) => [...scripts.values()].reduce((total, body) => total + compress(body).length, 0);
      assert.ok(bytes(withoutIslands) < bytes(countersOnly), 'loading a counter adds JavaScript');
      assert.ok(bytes(countersOnly) < bytes(allIslands), 'loading the list adds JavaScript');
    }
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
