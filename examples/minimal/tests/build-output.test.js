import assert from 'node:assert/strict';
import path from 'node:path';
import test from 'node:test';
import { JSDOM } from 'jsdom';
import { listFiles, read, root, workerFetch } from './support.js';

const serverOnlyMarker = 'hx4s-minimal-server-only-marker';
const trickyName = '</script><b>タグ</b> "引用" \'single\'';

async function page(pathname, init) {
  const response = await workerFetch(pathname, init);
  const html = await response.text();
  return { response, html, document: new JSDOM(html).window.document };
}

test('静的ページは JS 無しでも内容が読める SSR の HTML を返し、island を HonoX の境界で包む', async () => {
  const { response, html, document } = await page('/');
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /text\/html/);
  assert.ok(html.startsWith('<!DOCTYPE html>'));
  assert.equal(document.querySelector('h1').textContent, 'hx4s minimal');
  assert.equal(document.title, 'トップ | hx4s minimal');
  assert.deepEqual([...document.querySelectorAll('button.counter')].map((b) => b.textContent), ['A: 0', 'B: 10']);
  assert.equal(document.querySelector('.owner').textContent, '山田 太郎（2024 年から）');
  assert.deepEqual([...document.querySelectorAll('.item-list li button')].map((b) => b.textContent), ['りんご', trickyName, 'ぶどう😀']);
  assert.equal(document.querySelector('script[type="module"]').getAttribute('src'), '/static/client.js');
  assert.equal(document.querySelectorAll('script').length, 1, 'props の文字列が script 要素にならない');
  const islands = [...document.querySelectorAll('honox-island')];
  assert.deepEqual(islands.map((el) => el.getAttribute('component-name')),
    ['/app/islands/counter.tsx', '/app/islands/counter.tsx', '/app/islands/item-list.tsx']);
  const props = islands.map((el) => JSON.parse(el.getAttribute('data-serialized-props')));
  assert.deepEqual(props.slice(0, 2), [{ label: 'A', start: 0 }, { label: 'B', start: 10 }]);
  assert.deepEqual(props[2].owner, { name: '山田 太郎', since: 2024 });
  assert.deepEqual(props[2].items[1], { id: '2', name: trickyName, tags: ['記号', '<&>'] });
  assert.equal(props[2].note, '注記: </script><!-- "引用" & \'single\'');
  assert.ok(html.includes(serverOnlyMarker));
});

test('動的ルートはパラメータで描画し、無い ID は 404 を返す。HEAD は GET のハンドラが答える', async () => {
  const apple = await page('/items/1');
  assert.equal(apple.response.status, 200);
  assert.equal(apple.document.querySelector('.item-name').textContent, 'りんご');
  assert.equal(apple.document.querySelector('.item-tags').textContent, 'タグ: 果物, 赤');
  const tricky = await page('/items/2');
  assert.equal(tricky.document.querySelector('.item-name').textContent, trickyName);
  assert.doesNotMatch(tricky.html, /<\/script><b>/);
  const missing = await page('/items/999');
  assert.equal(missing.response.status, 404);
  assert.equal(missing.document.querySelector('h1').textContent, '見つかりません');
  const head = await workerFetch('/items/1', { method: 'HEAD' });
  assert.equal(head.status, 200);
});

test('POST のルートはフォームの値を検証して描画する', async () => {
  const form = (name) => ({ method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ name }).toString() });
  const saved = await page('/items/1', form(' 新しい名前 <b> '));
  assert.equal(saved.response.status, 200);
  assert.equal(saved.document.querySelector('.saved').textContent, '保存しました: 新しい名前 <b>');
  const rejected = await page('/items/1', form('  '));
  assert.equal(rejected.response.status, 400);
  assert.equal(rejected.document.querySelector('.error').textContent, '名前を入力してください');
  const missing = await page('/items/999', form('x'));
  assert.equal(missing.response.status, 400);
});

test('client のバンドルに server だけのコードが入らず、island の chunk は分かれている', () => {
  const clientFiles = listFiles(path.join(root, 'dist/client')).filter((file) => file.endsWith('.js'));
  const names = clientFiles.map((file) => path.relative(path.join(root, 'dist/client'), file).replaceAll('\\', '/'));
  assert.ok(names.includes('static/client.js'), names.join(', '));
  assert.ok(names.some((name) => /^static\/assets\/counter-.+\.js$/.test(name)), names.join(', '));
  assert.ok(names.some((name) => /^static\/assets\/item-list-.+\.js$/.test(name)), names.join(', '));
  const client = clientFiles.map(read).join('\n');
  for (const serverOnly of [serverOnlyMarker, '見つかりません', '名前を入力してください', 'HonoXIsland', 'renderShell']) {
    assert.ok(!client.includes(serverOnly), `client bundle contains ${serverOnly}`);
  }
  assert.ok(client.includes('選択中: '), 'client bundle contains the island code');
  assert.ok(read(path.join(root, 'dist/server/index.js')).includes(serverOnlyMarker));
  assert.ok(!listFiles(path.join(root, 'dist/client')).some((file) => file.endsWith('index.js')), 'Worker のコードを静的配信しない');
});
