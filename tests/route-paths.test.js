import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
// HonoX の内部モジュール（docs/hx4s/architecture.md 4 章 H11）。hx4s-codegen の RoutePath はこの変換を写している
import { createApp } from 'honox/server/server';

const table = fileURLToPath(new URL('../modules/codegen/src/test/resources/route-paths.tsv', import.meta.url));

test('RoutePathSuite と同じ表の各 route ファイルを、HonoX の createApp が表のとおりの URL パターンで登録する', async () => {
  const rows = (await readFile(table, 'utf8')).split('\n').filter((line) => line !== '' && !line.startsWith('#')).map((line) => line.split('\t'));
  assert.ok(rows.length > 10);
  for (const [file, expected] of rows) {
    const app = createApp({
      root: '/app/routes',
      ROUTES: { [`/app/routes/${file}`]: { default: () => new Response('ok') } },
      NOT_FOUND: {}, ERROR: {}, RENDERER: {}, MIDDLEWARE: {},
    });
    const paths = [...new Set(app.routes.filter((route) => route.method === 'GET').map((route) => route.path))];
    assert.deepEqual(paths, [expected], file);
  }
});
