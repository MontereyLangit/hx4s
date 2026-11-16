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
// npm の pretest が sbt react/Test/fastLinkJS で作る、実際の Scala のブリッジと client island
const bridge = path.join(root, 'modules/react/target/scala-3.3.8/hx4s-react-test-fastopt/island-test.js');

// examples/minimal/app/client.ts と同じ hydrate。props の検証関数（IslandExport が付ける）が失敗したら hydrate しない
const clientSource = `
import { createClient } from 'honox/client';
import { createElement } from 'react';
import { hydrateRoot } from 'react-dom/client';
export const roots = [];
export const errors = [];
export const skipped = [];
export function hydrate() {
  return createClient({
    createElement,
    hydrate: (element, root) => {
      const problem = element.type?.hx4sValidate?.(element.props);
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

/** fixture を作り、SSR 用（islandComponents 付き）と client 用（Node 向け）の 2 つをビルドして読み込む */
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
  // 本番の client ビルドと同じく、SSR の island 変換プラグインは入れない
  await build({ ...config,
    build: { ssr: path.join(fixture, 'client.js'), outDir: path.join(fixture, 'client-out'),
      rollupOptions: { output: { entryFileNames: 'entry.mjs', chunkFileNames: '[name]-[hash].mjs' } } } });
  return {
    fixture,
    server: await import(pathToFileURL(path.join(fixture, 'server-out/entry.mjs')).href),
    client: await import(pathToFileURL(path.join(fixture, 'client-out/entry.mjs')).href),
  };
}

/** jsdom の window / document を globalThis に置いて body を実行し、終わったら戻す */
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

/** React の dev ビルドを Vite と同じプロセスで揃える */
async function inDevelopment(body) {
  const previousNodeEnv = process.env.NODE_ENV;
  process.env.NODE_ENV = 'development';
  try { return await body(); }
  finally {
    if (previousNodeEnv === undefined) delete process.env.NODE_ENV;
    else process.env.NODE_ENV = previousNodeEnv;
  }
}

test('Scala の Island は HonoX の検出・入れ子 island・要素 props に対応する', (t) => inDevelopment(async () => {
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
    // SSR はリンクした Scala のブリッジと素のコンポーネントを使い、app/islands/*.tsx は import しない
    'server.js': `
import { createElement, cloneElement } from 'react';
import { renderToString } from 'react-dom/server';
import { renderIslandTest } from 'scala-island-test';
import { Counter, Slot } from './components.js';
const island = (Component, props, name) => renderIslandTest(Component, props, '/app/islands/' + name + '.tsx');
const NestedParent = props => createElement('div', null, island(Counter, props, 'counter'));
export function renderCase(kind) {
  const props = { label: '日本語 "<&>' };
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
            assert.deepEqual(JSON.parse(boundary.getAttribute('data-serialized-props')), { label: '日本語 "<&>' });
          }
          const originalButton = document.querySelector('button');
          await act(async () => { await client.hydrate(); });
          assert.deepEqual(client.errors, [], 'hydration must match the SSR markup');
          assert.equal(client.roots.length, 1, 'nested islands must not create competing hydration roots');
          assert.equal(document.querySelector('button'), originalButton, 'hydration must reuse the server DOM');
          if (kind === 'slot') assert.equal(document.querySelector('span').textContent, 'slot <&>');
          await act(async () => { originalButton.click(); });
          assert.equal(originalButton.textContent, '日本語 "<&>:1');
        } finally {
          await cleanup(client);
        }
      }));
    }
  } finally {
    await rm(fixture, { recursive: true, force: true });
  }
}));

test('island の props は危険な文字列・配列・入れ子・同じ island の複数インスタンスでも安全に往復する', () => inDevelopment(async () => {
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
    const tricky = '日本語😀 改行</script><script>alert(1)</script><!-- "引用" \'single\' &amp; \\ ';
    const first = { label: tricky, items: [{ id: 'a', text: tricky }, { id: 'b', text: '</SCRIPT>' }], nested: { list: [1, 2.5, true, null], deep: { 名前: '値' } } };
    const second = { label: '2つ目', items: [], nested: { list: [] } };
    const html = server.render(first, second);
    assert.doesNotMatch(html, /<\/script>|<script>|<!--/i, 'props の文字列が HTML の構造として出ない');
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
        assert.equal(client.roots.length, 2, '同じ island の各インスタンスがそれぞれ hydrate される');
        assert.deepEqual(client.received, [first, second], 'hydration 後も同じ値で読める');
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

test('Scala.js の client island（IslandExport）を hydrate し、props が合わない HTML では hydrate せずに SSR の markup を残す', () => inDevelopment(async () => {
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
        // 旧い世代の HTML: start を持たない props
        old.setAttribute('data-serialized-props', JSON.stringify({ label: 'old' }));
        const buttons = [...document.querySelectorAll('button')];
        assert.deepEqual(buttons.map(b => b.textContent), ['a:1', 'b:5', 'old:0']);
        await act(async () => { await client.hydrate(); });
        assert.deepEqual(client.errors, []);
        assert.equal(client.roots.length, 2, '検証を通った 2 つだけを hydrate する');
        assert.deepEqual(client.skipped, ['props.start: missing']);
        assert.ok(consoleErrors.some(message => message.includes('props.start: missing')), consoleErrors.join('\n'));
        await act(async () => { for (const button of buttons) button.click(); });
        assert.deepEqual(buttons.map(b => b.textContent), ['a:2', 'b:6', 'old:0'], 'hydrate しなかった island は SSR の markup のまま残る');
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
