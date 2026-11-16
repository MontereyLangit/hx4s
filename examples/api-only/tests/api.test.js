import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const workerFile = path.join(root, 'dist/server/index.js');

/** ビルドした Worker を Node で読み込み、fetch を呼ぶ */
async function workerFetch(pathname, init) {
  const worker = (await import(pathToFileURL(workerFile).href)).default;
  return worker.fetch(new Request(`http://localhost${pathname}`, init), {}, { waitUntil() {}, passThroughOnException() {} });
}

const postJson = (body) => ({ method: 'POST', headers: { 'content-type': 'application/json' }, body });

test('GET は一覧と 1 件を JSON で返し、無い ID は c.notFound() の 404 になる', async () => {
  const list = await workerFetch('/api/notes');
  assert.equal(list.status, 200);
  assert.match(list.headers.get('content-type'), /application\/json/);
  assert.deepEqual(await list.json(), [{ id: '1', text: '牛乳を買う' }, { id: '2', text: '本を返す' }]);
  const one = await workerFetch('/api/notes/2');
  assert.deepEqual(await one.json(), { id: '2', text: '本を返す' });
  const missing = await workerFetch('/api/notes/999');
  assert.deepEqual([missing.status, await missing.text()], [404, '404 Not Found']);
});

test('POST は本文の形と値を確かめ、201 か 400 を返す', async () => {
  const created = await workerFetch('/api/notes', postJson(JSON.stringify({ text: '  傘を持つ ' })));
  assert.equal(created.status, 201);
  assert.deepEqual(await created.json(), { id: '3', text: '傘を持つ' });
  const blank = await workerFetch('/api/notes', postJson(JSON.stringify({ text: ' ' })));
  assert.deepEqual([blank.status, await blank.json()], [400, { error: '本文を入力してください' }]);
  const wrongShape = await workerFetch('/api/notes', postJson(JSON.stringify({ text: 1 })));
  assert.equal(wrongShape.status, 400);
  assert.match((await wrongShape.json()).error, /props\.text: expected string, got number/);
  const notJson = await workerFetch('/api/notes', postJson('{'));
  assert.deepEqual([notJson.status, await notJson.json()], [400, { error: '本文が JSON ではありません' }]);
});

test('DELETE は c.body(null, 204) で本文なしの 204 を返し、無い ID は 404 になる', async () => {
  const deleted = await workerFetch('/api/notes/1', { method: 'DELETE' });
  assert.equal(deleted.status, 204);
  assert.equal(deleted.body, null);
  assert.equal(deleted.headers.get('content-type'), null);
  const missing = await workerFetch('/api/notes/999', { method: 'DELETE' });
  assert.equal(missing.status, 404);
});

test('Worker のバンドルは React を含まない', () => {
  const bundle = readFileSync(workerFile, 'utf8');
  for (const marker of ['react-dom', 'renderToReadableStream', 'react.transitional.element']) {
    assert.ok(!bundle.includes(marker), `the worker bundle contains ${marker}`);
  }
});
