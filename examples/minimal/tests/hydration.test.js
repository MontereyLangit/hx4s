import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import test from 'node:test';
import { build } from 'vite';
import { JSDOM } from 'jsdom';
import { linkedClientDirectory, root, workerFetch } from './support.js';

/** Waits until the condition holds */
async function waitFor(condition, message) {
  for (let i = 0; i < 200; i++) {
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve, 25));
  }
  assert.fail(message);
}

test('on jsdom, app/client.ts hydrates Scala.js islands and leaves islands with mismatched props as SSR markup', async () => {
  // Rebuild app/client.ts in SSR format, keeping the same island discovery as production, so that Node can load it
  const out = await mkdtemp(path.join(root, 'tests', '.hydration-'));
  const previous = new Map();
  const consoleErrors = [];
  const originalError = console.error;
  let dom;
  try {
    await build({
      configFile: false, root, logLevel: 'silent',
      resolve: { alias: [{ find: /^scalajs:/, replacement: linkedClientDirectory().replaceAll('\\', '/') + '/' }] },
      ssr: { noExternal: ['honox'] },
      build: { ssr: path.join(root, 'app/client.ts'), outDir: out, emptyOutDir: true,
        rollupOptions: { output: { entryFileNames: 'client.mjs', chunkFileNames: '[name]-[hash].mjs' } } },
    });
    const html = await (await workerFetch('/')).text();
    dom = new JSDOM(html);
    const document = dom.window.document;
    // Give the third island (counter B) props of a different shape, like HTML from an older version
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
    await waitFor(() => document.querySelector('.selected-name').textContent === 'Selected: apple', 'item list did not update');
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
