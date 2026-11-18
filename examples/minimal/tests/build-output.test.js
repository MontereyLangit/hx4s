import assert from 'node:assert/strict';
import path from 'node:path';
import test from 'node:test';
import { JSDOM } from 'jsdom';
import { listFiles, read, root, workerFetch } from './support.js';

const serverOnlyMarker = 'hx4s-minimal-server-only-marker';
const trickyName = '</script><b>tag</b> "quoted" \'single\'';

async function page(pathname, init) {
  const response = await workerFetch(pathname, init);
  const html = await response.text();
  return { response, html, document: new JSDOM(html).window.document };
}

test('the static page returns SSR HTML readable without JS, wrapping islands in HonoX boundaries', async () => {
  const { response, html, document } = await page('/');
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /text\/html/);
  assert.ok(html.startsWith('<!DOCTYPE html>'));
  assert.equal(document.querySelector('h1').textContent, 'hx4s minimal');
  assert.equal(document.title, 'Home | hx4s minimal');
  assert.deepEqual([...document.querySelectorAll('button.counter')].map((b) => b.textContent), ['A: 0', 'B: 10']);
  assert.equal(document.querySelector('.owner').textContent, 'Zoë Smith (since 2024)');
  assert.deepEqual([...document.querySelectorAll('.item-list li button')].map((b) => b.textContent), ['apple', trickyName, 'grape😀']);
  assert.equal(document.querySelector('script[type="module"]').getAttribute('src'), '/static/client.js');
  assert.equal(document.querySelectorAll('script').length, 1, 'props strings do not become script elements');
  const islands = [...document.querySelectorAll('honox-island')];
  assert.deepEqual(islands.map((el) => el.getAttribute('component-name')),
    ['/app/islands/counter.tsx', '/app/islands/counter.tsx', '/app/islands/item-list.tsx']);
  const props = islands.map((el) => JSON.parse(el.getAttribute('data-serialized-props')));
  assert.deepEqual(props.slice(0, 2), [{ label: 'A', start: 0 }, { label: 'B', start: 10 }]);
  assert.deepEqual(props[2].owner, { name: 'Zoë Smith', since: 2024 });
  assert.deepEqual(props[2].items[1], { id: '2', name: trickyName, tags: ['symbols', '<&>'] });
  assert.equal(props[2].note, 'Note: </script><!-- "quoted" & \'single\'');
  assert.ok(html.includes(serverOnlyMarker));
});

test('dynamic routes render from the parameter and return 404 for unknown IDs; HEAD is answered by the GET handler', async () => {
  const apple = await page('/items/1');
  assert.equal(apple.response.status, 200);
  assert.equal(apple.document.querySelector('.item-name').textContent, 'apple');
  assert.equal(apple.document.querySelector('.item-tags').textContent, 'Tags: fruit, red');
  const tricky = await page('/items/2');
  assert.equal(tricky.document.querySelector('.item-name').textContent, trickyName);
  assert.doesNotMatch(tricky.html, /<\/script><b>/);
  const missing = await page('/items/999');
  assert.equal(missing.response.status, 404);
  assert.equal(missing.document.querySelector('h1').textContent, 'No such item');
  const head = await workerFetch('/items/1', { method: 'HEAD' });
  assert.equal(head.status, 200);
});

test('the POST route validates and renders the form values', async () => {
  const form = (name) => ({ method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ name }).toString() });
  const saved = await page('/items/1', form(' New name <b> '));
  assert.equal(saved.response.status, 200);
  assert.equal(saved.document.querySelector('.saved').textContent, 'Saved: New name <b>');
  const rejected = await page('/items/1', form('  '));
  assert.equal(rejected.response.status, 400);
  assert.equal(rejected.document.querySelector('.error').textContent, 'Enter an item name');
  const missing = await page('/items/999', form('x'));
  assert.equal(missing.response.status, 400);
});

test('the API route returns with c.json and reads repeated ?tag= with c.req.queries', async () => {
  const all = await workerFetch('/api/items');
  assert.equal(all.status, 200);
  assert.match(all.headers.get('content-type'), /application\/json/);
  assert.deepEqual((await all.json()).map((item) => item.id), ['1', '2', '3']);
  const fruit = await (await workerFetch(`/api/items?tag=${encodeURIComponent('fruit')}&tag=${encodeURIComponent('red')}`)).json();
  assert.deepEqual(fruit, [{ id: '1', name: 'apple', tags: ['fruit', 'red'] }]);
});

test('the client bundle has no server-only code, and island chunks are separate', () => {
  const clientFiles = listFiles(path.join(root, 'dist/client')).filter((file) => file.endsWith('.js'));
  const names = clientFiles.map((file) => path.relative(path.join(root, 'dist/client'), file).replaceAll('\\', '/'));
  assert.ok(names.includes('static/client.js'), names.join(', '));
  assert.ok(names.some((name) => /^static\/assets\/counter-.+\.js$/.test(name)), names.join(', '));
  assert.ok(names.some((name) => /^static\/assets\/item-list-.+\.js$/.test(name)), names.join(', '));
  const client = clientFiles.map(read).join('\n');
  for (const serverOnly of [serverOnlyMarker, 'No such item', 'Enter an item name', 'HonoXIsland', 'renderShell']) {
    assert.ok(!client.includes(serverOnly), `client bundle contains ${serverOnly}`);
  }
  assert.ok(client.includes('Selected: '), 'client bundle contains the island code');
  assert.ok(read(path.join(root, 'dist/server/index.js')).includes(serverOnlyMarker));
  assert.ok(!listFiles(path.join(root, 'dist/client')).some((file) => file.endsWith('index.js')), 'the Worker code is not served statically');
});
