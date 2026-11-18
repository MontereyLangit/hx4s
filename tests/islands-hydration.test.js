import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm, access } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import test from 'node:test';
import { build } from 'vite';
import { islandComponents } from 'honox/vite';
import { JSDOM } from 'jsdom';
import { act } from 'react';

const root = fileURLToPath(new URL('../', import.meta.url));
// The real Scala bridge and client island, built by npm's pretest via sbt react/Test/fastLinkJS
const bridge = path.join(root, 'modules/react/target/scala-3.3.8/hx4s-react-test-fastopt/island-test.js');

// The same hydrate as examples/minimal/app/client.ts. Does not hydrate if props validation (attached by IslandExport) fails for the island or islands inside templates
const clientSource = `
import { createClient } from 'honox/client';
import { createElement } from 'react';
import { hydrateRoot } from 'react-dom/client';
import { islandPropsProblem } from 'hx4s-vite/client';
export const roots = [];
export const errors = [];
export const skipped = [];
export function hydrate() {
  return createClient({
    createElement,
    hydrate: (element, root) => {
      const problem = islandPropsProblem(element);
      if (problem !== undefined) {
        skipped.push(problem);
        console.error('[hx4s] skipped hydration: ' + problem);
        return;
      }
      roots.push(hydrateRoot(root, element, { onRecoverableError: error => errors.push(error.message) }));
    }
  });
}
`;

/** Creates the fixture, builds it twice, for SSR (with islandComponents) and for the client (targeting Node), and loads both */
async function buildFixture(name, files) {
  await access(bridge);
  const fixture = await mkdtemp(path.join(root, `tests/.${name}-`));
  await mkdir(path.join(fixture, 'app/islands'), { recursive: true });
  for (const [file, content] of Object.entries({ 'client.js': clientSource, ...files })) {
    await mkdir(path.dirname(path.join(fixture, file)), { recursive: true });
    await writeFile(path.join(fixture, file), content);
  }
  const config = {
    configFile: false, root: fixture, logLevel: 'silent',
    resolve: { alias: { 'scala-island-test': bridge } },
    ssr: { noExternal: ['honox'] },
  };
  await build({ ...config, plugins: [islandComponents({ reactApiImportSource: 'react' })],
    build: { ssr: path.join(fixture, 'server.js'), outDir: path.join(fixture, 'server-out'),
      rollupOptions: { output: { entryFileNames: 'entry.mjs' } } } });
  // Like the production client build, the SSR island transform plugin is not included
  await build({ ...config,
    build: { ssr: path.join(fixture, 'client.js'), outDir: path.join(fixture, 'client-out'),
      rollupOptions: { output: { entryFileNames: 'entry.mjs', chunkFileNames: '[name]-[hash].mjs' } } } });
  return {
    fixture,
    server: await import(pathToFileURL(path.join(fixture, 'server-out/entry.mjs')).href),
    client: await import(pathToFileURL(path.join(fixture, 'client-out/entry.mjs')).href),
  };
}

/** Puts jsdom's window / document on globalThis, runs body, and restores them afterwards */
async function withDom(html, body) {
  const dom = new JSDOM(`<!DOCTYPE html><html><body>${html}</body></html>`);
  const globals = { window: dom.window, document: dom.window.document, Node: dom.window.Node, IS_REACT_ACT_ENVIRONMENT: true };
  const previous = new Map(Object.keys(globals).map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]));
  for (const [key, value] of Object.entries(globals)) Object.defineProperty(globalThis, key, { value, configurable: true, writable: true });
  try {
    return await body(dom.window.document);
  } finally {
    dom.window.close();
    for (const [key, descriptor] of previous) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor);
      else delete globalThis[key];
    }
  }
}

async function cleanup(client) {
  await act(async () => { for (const reactRoot of client.roots.splice(0)) reactRoot.unmount(); });
  client.errors.length = 0;
  client.skipped.length = 0;
}

/** Aligns React's dev build with Vite in the same process */
async function inDevelopment(body) {
  const previousNodeEnv = process.env.NODE_ENV;
  process.env.NODE_ENV = 'development';
  try { return await body(); }
  finally {
    if (previousNodeEnv === undefined) delete process.env.NODE_ENV;
    else process.env.NODE_ENV = previousNodeEnv;
  }
}

test('the Scala Island supports HonoX detection, nested islands and element props', (t) => inDevelopment(async () => {
  const { fixture, server, client } = await buildFixture('islands-hydration', {
    'components.js': `
import { createElement, useState } from 'react';
export function Counter({ label }) {
  const [count, setCount] = useState(0);
  return createElement('button', { onClick: () => setCount(n => n + 1) }, label + ':' + count);
}
export function Parent(props) { return createElement('div', null, createElement(Counter, props)); }
export function Slot({ children }) { return createElement('section', null, children); }
`,
    'app/islands/counter.tsx': "import { Counter } from '../../components.js';\nexport default Counter;\n",
    'app/islands/parent.tsx': "import { Parent } from '../../components.js';\nexport default Parent;\n",
    'app/islands/slot.tsx': "import { Slot } from '../../components.js';\nexport default Slot;\n",
    // SSR uses the linked Scala bridge and plain components, and does not import app/islands/*.tsx
    'server.js': `
import { createElement, cloneElement } from 'react';
import { renderToString } from 'react-dom/server';
import { renderIslandTest } from 'scala-island-test';
import { Counter, Slot } from './components.js';
const island = (Component, props, name) => renderIslandTest(Component, props, '/app/islands/' + name + '.tsx');
const NestedParent = props => createElement('div', null, island(Counter, props, 'counter'));
export function renderCase(kind) {
  const props = { label: 'Ünïcödé "<&>' };
  if (kind === 'nested') return renderToString(island(NestedParent, props, 'parent'));
  if (kind === 'slot') return renderToString(island(Slot, {
    children: [createElement('span', { key: 'text' }, 'slot <&>'),
      cloneElement(island(Counter, props, 'counter'), { key: 'counter' })]
  }, 'slot'));
  return renderToString(island(Counter, props, 'counter'));
}
`,
  });
  try {
    for (const kind of ['simple', 'nested', 'slot']) {
      await t.test(kind, () => withDom(server.renderCase(kind), async (document) => {
        try {
          assert.equal(document.querySelectorAll('honox-island').length, 1);
          const boundary = document.querySelector('honox-island');
          assert.equal(boundary.getAttribute('component-name'), `/app/islands/${kind === 'simple' ? 'counter' : kind === 'nested' ? 'parent' : 'slot'}.tsx`);
          if (kind === 'slot') {
            assert.equal(boundary.getAttribute('data-serialized-props'), '{}');
            const template = boundary.querySelector('template[data-hono-template="children"]');
            assert.ok(template);
            assert.equal(template.content.querySelectorAll('honox-island').length, 1);
          } else {
            assert.deepEqual(JSON.parse(boundary.getAttribute('data-serialized-props')), { label: 'Ünïcödé "<&>' });
          }
          const originalButton = document.querySelector('button');
          await act(async () => { await client.hydrate(); });
          assert.deepEqual(client.errors, [], 'hydration must match the SSR markup');
          assert.equal(client.roots.length, 1, 'nested islands must not create competing hydration roots');
          assert.equal(document.querySelector('button'), originalButton, 'hydration must reuse the server DOM');
          if (kind === 'slot') assert.equal(document.querySelector('span').textContent, 'slot <&>');
          await act(async () => { originalButton.click(); });
          assert.equal(originalButton.textContent, 'Ünïcödé "<&>:1');
        } finally {
          await cleanup(client);
        }
      }));
    }
  } finally {
    await rm(fixture, { recursive: true, force: true });
  }
}));

test('island props round-trip safely with dangerous strings, arrays, nesting and multiple instances of the same island', () => inDevelopment(async () => {
  const { fixture, server, client } = await buildFixture('islands-props', {
    'components.js': `
import { createElement } from 'react';
export const received = [];
export function Echo(props) {
  received.push(JSON.parse(JSON.stringify(props)));
  return createElement('ul', { 'data-label': props.label }, props.items.map(item => createElement('li', { key: item.id }, item.text)));
}
`,
    'app/islands/echo.tsx': "import { Echo } from '../../components.js';\nexport default Echo;\n",
    'server.js': `
import { createElement } from 'react';
import { renderToString } from 'react-dom/server';
import { renderIslandTest } from 'scala-island-test';
import { Echo } from './components.js';
export function render(first, second) {
  return renderToString(createElement('main', null,
    renderIslandTest(Echo, first, '/app/islands/echo.tsx'),
    renderIslandTest(Echo, second, '/app/islands/echo.tsx')));
}
`,
    'client.js': `${clientSource}\nexport { received } from './components.js';\n`,
  });
  try {
    const tricky = 'Ünïcödé😀 newline</script><script>alert(1)</script><!-- "quoted" \'single\' &amp; \\ ';
    const first = { label: tricky, items: [{ id: 'a', text: tricky }, { id: 'b', text: '</SCRIPT>' }], nested: { list: [1, 2.5, true, null], deep: { clé: 'välue' } } };
    const second = { label: 'second', items: [], nested: { list: [] } };
    const html = server.render(first, second);
    assert.doesNotMatch(html, /<\/script>|<script>|<!--/i, 'props strings do not appear as HTML structure');
    await withDom(html, async (document) => {
      try {
        const boundaries = [...document.querySelectorAll('honox-island')];
        assert.equal(boundaries.length, 2);
        assert.deepEqual(boundaries.map(el => JSON.parse(el.getAttribute('data-serialized-props'))), [first, second]);
        assert.equal(document.querySelectorAll('script').length, 0);
        client.received.length = 0;
        const lists = [...document.querySelectorAll('ul')];
        await act(async () => { await client.hydrate(); });
        assert.deepEqual(client.errors, [], 'hydration must match the SSR markup');
        assert.equal(client.roots.length, 2, 'each instance of the same island is hydrated');
        assert.deepEqual(client.received, [first, second], 'the same values are read after hydration');
        assert.deepEqual([...document.querySelectorAll('ul')], lists);
        assert.equal(lists[0].getAttribute('data-label'), tricky);
        assert.equal(lists[0].querySelector('li').textContent, tricky);
      } finally {
        await cleanup(client);
      }
    });
  } finally {
    await rm(fixture, { recursive: true, force: true });
  }
}));

test('hydrates Scala.js client islands (IslandExport), and keeps the SSR markup without hydrating for HTML with mismatched props', () => inDevelopment(async () => {
  const { fixture, server, client } = await buildFixture('islands-scala-client', {
    'app/islands/counter.tsx': "import { CounterTest } from 'scala-island-test';\nexport default CounterTest;\n",
    'server.js': `
import { createElement } from 'react';
import { renderToString } from 'react-dom/server';
import { renderIslandTest, CounterTest } from 'scala-island-test';
export function render() {
  return renderToString(createElement('main', null,
    renderIslandTest(CounterTest, { label: 'a', start: 1 }, '/app/islands/counter.tsx'),
    renderIslandTest(CounterTest, { label: 'b', start: 5 }, '/app/islands/counter.tsx'),
    renderIslandTest(CounterTest, { label: 'old', start: 0 }, '/app/islands/counter.tsx')));
}
`,
  });
  const consoleErrors = [];
  const originalError = console.error;
  console.error = (...args) => { consoleErrors.push(args.join(' ')); };
  try {
    await withDom(server.render(), async (document) => {
      try {
        const [, , old] = document.querySelectorAll('honox-island');
        // HTML from an older version: props without start
        old.setAttribute('data-serialized-props', JSON.stringify({ label: 'old' }));
        const buttons = [...document.querySelectorAll('button')];
        assert.deepEqual(buttons.map(b => b.textContent), ['a:1', 'b:5', 'old:0']);
        await act(async () => { await client.hydrate(); });
        assert.deepEqual(client.errors, []);
        assert.equal(client.roots.length, 2, 'only the two that pass validation are hydrated');
        assert.deepEqual(client.skipped, ['props.start: missing']);
        assert.ok(consoleErrors.some(message => message.includes('props.start: missing')), consoleErrors.join('\n'));
        await act(async () => { for (const button of buttons) button.click(); });
        assert.deepEqual(buttons.map(b => b.textContent), ['a:2', 'b:6', 'old:0'], 'islands that were not hydrated keep the SSR markup');
        assert.deepEqual([...document.querySelectorAll('button')], buttons);
      } finally {
        await cleanup(client);
      }
    });
  } finally {
    console.error = originalError;
    await rm(fixture, { recursive: true, force: true });
  }
}));

test('if a Scala.js island inside element props (template) has mismatched props, the outer island is not hydrated either and keeps the SSR markup', () => inDevelopment(async () => {
  const { fixture, server, client } = await buildFixture('islands-template-validation', {
    'components.js': `
import { createElement } from 'react';
export function Slot({ children }) { return createElement('section', null, children); }
`,
    'app/islands/slot.tsx': "import { Slot } from '../../components.js';\nexport default Slot;\n",
    'app/islands/counter.tsx': "import { CounterTest } from 'scala-island-test';\nexport default CounterTest;\n",
    'server.js': `
import { createElement, cloneElement } from 'react';
import { renderToString } from 'react-dom/server';
import { renderIslandTest, CounterTest } from 'scala-island-test';
import { Slot } from './components.js';
export function render() {
  const inner = renderIslandTest(CounterTest, { label: 'in', start: 3 }, '/app/islands/counter.tsx');
  return renderToString(renderIslandTest(Slot, { children: [createElement('span', { key: 'text' }, 'slot'), cloneElement(inner, { key: 'counter' })] },
    '/app/islands/slot.tsx'));
}
`,
  });
  const consoleErrors = [];
  const originalError = console.error;
  console.error = (...args) => { consoleErrors.push(args.join(' ')); };
  try {
    for (const valid of [true, false]) {
      await withDom(server.render(), async (document) => {
        try {
          const inner = document.querySelector('template[data-hono-template="children"]').content.querySelector('honox-island');
          assert.equal(inner.getAttribute('component-name'), '/app/islands/counter.tsx');
          // HTML from an older version: the props of the island inside the template have no start
          if (!valid) inner.setAttribute('data-serialized-props', JSON.stringify({ label: 'in' }));
          const button = document.querySelector('button');
          assert.equal(button.textContent, 'in:3');
          await act(async () => { await client.hydrate(); });
          await act(async () => { button.click(); });
          assert.deepEqual(client.errors, []);
          if (valid) {
            assert.equal(client.roots.length, 1);
            assert.deepEqual(client.skipped, []);
            assert.equal(button.textContent, 'in:4');
          } else {
            assert.equal(client.roots.length, 0, 'the outer island is not hydrated either');
            assert.deepEqual(client.skipped, ['props.children[1]: props.start: missing']);
            assert.equal(button.textContent, 'in:3', 'the SSR markup remains');
            assert.equal(document.querySelector('span').textContent, 'slot');
          }
        } finally {
          await cleanup(client);
        }
      });
    }
    assert.ok(consoleErrors.some(message => message.includes('props.children[1]: props.start: missing')), consoleErrors.join('\n'));
  } finally {
    console.error = originalError;
    await rm(fixture, { recursive: true, force: true });
  }
}));
