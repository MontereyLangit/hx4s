import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import test from 'node:test';
import { build } from 'vite';
import { JSDOM } from 'jsdom';
import { linkedClientModule, root, workerFetch } from './support.js';

/** 条件が満たされるまで待つ */
async function waitFor(condition, message) {
  for (let i = 0; i < 200; i++) {
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve, 25));
  }
  assert.fail(message);
}

test('jsdom 上で app/client.ts が Scala.js の island を hydrate し、props が合わない island は SSR の markup のまま残す', async () => {
  // app/client.ts を Node で読み込めるよう、本番と同じ island の探索のまま SSR 形式でビルドし直す
  const out = await mkdtemp(path.join(root, 'tests', '.hydration-'));
  const previous = new Map();
  const consoleErrors = [];
  const originalError = console.error;
  let dom;
  try {
    await build({
      configFile: false, root, logLevel: 'silent',
      resolve: { alias: { 'scalajs:client.js': linkedClientModule() } },
      ssr: { noExternal: ['honox'] },
      build: { ssr: path.join(root, 'app/client.ts'), outDir: out, emptyOutDir: true,
        rollupOptions: { output: { entryFileNames: 'client.mjs', chunkFileNames: '[name]-[hash].mjs' } } },
    });
    const html = await (await workerFetch('/')).text();
    dom = new JSDOM(html);
    const document = dom.window.document;
    // 3 つ目の island（B のカウンター）は、古い世代の HTML のように props の形が違うものにする
    const counters = [...document.querySelectorAll('honox-island[component-name="/app/islands/counter.tsx"]')];
    counters[1].setAttribute('data-serialized-props', JSON.stringify({ label: 'B' }));
    const globals = { window: dom.window, document, Node: dom.window.Node, HTMLElement: dom.window.HTMLElement };
    for (const [key, value] of Object.entries(globals)) {
      previous.set(key, Object.getOwnPropertyDescriptor(globalThis, key));
      Object.defineProperty(globalThis, key, { value, configurable: true, writable: true });
    }
    console.error = (...args) => { consoleErrors.push(args.map(String).join(' ')); };

    const buttons = () => [...document.querySelectorAll('button.counter')];
    const originalA = buttons()[0];
    await import(pathToFileURL(path.join(out, 'client.mjs')).href);
    await waitFor(() => document.documentElement.hasAttribute('data-hx4s-ready'), 'createClient did not finish');

    originalA.click();
    await waitFor(() => originalA.textContent === 'A: 1', `counter A did not update: ${originalA.textContent}`);
    assert.equal(buttons()[0], originalA, 'hydration reuses the server DOM');
    buttons()[1].click();
    await new Promise((resolve) => setTimeout(resolve, 100));
    assert.equal(buttons()[1].textContent, 'B: 10', 'the island with mismatched props is left as SSR markup');

    const firstItem = document.querySelector('.item-list li button');
    firstItem.click();
    await waitFor(() => document.querySelector('.selected-name').textContent === '選択中: りんご', 'item list did not update');
    assert.ok(document.querySelector('.item-list li').classList.contains('selected'));

    assert.deepEqual(consoleErrors.filter((message) => !message.includes('skipped hydration')), [], 'no hydration errors');
    assert.equal(consoleErrors.filter((message) => message.includes('skipped hydration of /app/islands/counter.tsx: props.start: missing')).length, 1,
      consoleErrors.join('\n'));
  } finally {
    console.error = originalError;
    dom?.window.close();
    for (const [key, descriptor] of previous) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor);
      else delete globalThis[key];
    }
    await rm(out, { recursive: true, force: true });
  }
});
