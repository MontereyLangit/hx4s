import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const workerFile = path.join(root, 'dist/server/index.js');

/** Loads the built Worker on Node and calls fetch */
async function workerFetch(pathname, init) {
  const worker = (await import(pathToFileURL(workerFile).href)).default;
  return worker.fetch(new Request(`http://localhost${pathname}`, init), {}, { waitUntil() {}, passThroughOnException() {} });
}

const postJson = (body) => ({ method: 'POST', headers: { 'content-type': 'application/json' }, body });

test('GET returns the list and single notes as JSON, and an unknown ID gets the 404 of c.notFound()', async () => {
  const list = await workerFetch('/api/notes');
  assert.equal(list.status, 200);
  assert.match(list.headers.get('content-type'), /application\/json/);
  assert.deepEqual(await list.json(), [{ id: '1', text: 'Buy milk 🥛' }, { id: '2', text: 'Return the book' }]);
  const one = await workerFetch('/api/notes/2');
  assert.deepEqual(await one.json(), { id: '2', text: 'Return the book' });
  const missing = await workerFetch('/api/notes/999');
  assert.deepEqual([missing.status, await missing.text()], [404, '404 Not Found']);
});

test('POST validates the shape and values of the body and returns 201 or 400', async () => {
  const created = await workerFetch('/api/notes', postJson(JSON.stringify({ text: '  Take an umbrella ☂ ' })));
  assert.equal(created.status, 201);
  assert.deepEqual(await created.json(), { id: '3', text: 'Take an umbrella ☂' });
  const blank = await workerFetch('/api/notes', postJson(JSON.stringify({ text: ' ' })));
  assert.deepEqual([blank.status, await blank.json()], [400, { error: 'Enter the note text' }]);
  const wrongShape = await workerFetch('/api/notes', postJson(JSON.stringify({ text: 1 })));
  assert.equal(wrongShape.status, 400);
  assert.match((await wrongShape.json()).error, /props\.text: expected string, got number/);
  const notJson = await workerFetch('/api/notes', postJson('{'));
  assert.deepEqual([notJson.status, await notJson.json()], [400, { error: 'The body is not JSON' }]);
});

test('DELETE returns 204 without a body via c.body(null, 204), and an unknown ID gets 404', async () => {
  const deleted = await workerFetch('/api/notes/1', { method: 'DELETE' });
  assert.equal(deleted.status, 204);
  assert.equal(deleted.body, null);
  assert.equal(deleted.headers.get('content-type'), null);
  const missing = await workerFetch('/api/notes/999', { method: 'DELETE' });
  assert.equal(missing.status, 404);
});

test('the Worker bundle does not contain React', () => {
  const bundle = readFileSync(workerFile, 'utf8');
  for (const marker of ['react-dom', 'renderToReadableStream', 'react.transitional.element']) {
    assert.ok(!bundle.includes(marker), `the worker bundle contains ${marker}`);
  }
});
