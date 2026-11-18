import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
// HonoX's internal module (docs/hx4s/architecture.md chapter 4, H11). hx4s-codegen's RoutePath mirrors this conversion
import { createApp } from 'honox/server/server';

const table = fileURLToPath(new URL('../modules/codegen/src/test/resources/route-paths.tsv', import.meta.url));

test('HonoX createApp registers each route file of the same table as RoutePathSuite with the URL pattern in the table', async () => {
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
