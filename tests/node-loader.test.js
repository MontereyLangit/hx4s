import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = path.resolve(fileURLToPath(new URL('..', import.meta.url)));

test('hx4s-vite/node-loader can be registered alongside a user loader, and each resolves its own specifiers', async () => {
  // Put the same setup as the example in build-integration.md 2.3 where hx4s-vite resolves from the root node_modules
  await mkdir(path.join(root, 'target'), { recursive: true });
  const dir = await mkdtemp(path.join(root, 'target', 'loader compose & '));
  try {
    await mkdir(path.join(dir, 'client'));
    await writeFile(path.join(dir, 'client', 'client.js'), 'export const from = "client";\n');
    await writeFile(path.join(dir, 'runtime-stub.mjs'), 'export const from = "runtime stub";\n');
    await writeFile(path.join(dir, 'runtime-loader.mjs'), [
      'export async function resolve(specifier, context, nextResolve) {',
      '  if (specifier === "cloudflare:workers") return { url: new URL("./runtime-stub.mjs", import.meta.url).href, shortCircuit: true };',
      '  return nextResolve(specifier, context);',
      '}',
    ].join('\n'));
    await writeFile(path.join(dir, 'register.mjs'), [
      'import { register } from "node:module";',
      'register("hx4s-vite/node-loader", import.meta.url);',
      'register("./runtime-loader.mjs", import.meta.url);',
    ].join('\n'));
    await writeFile(path.join(dir, 'main.mjs'), [
      'import { from as client } from "scalajs:client.js";',
      'import { from as runtime } from "cloudflare:workers";',
      'console.log(JSON.stringify([client, runtime]));',
    ].join('\n'));
    const result = spawnSync(process.execPath, ['--import', './register.mjs', 'main.mjs'], {
      cwd: dir,
      encoding: 'utf8',
      env: { ...process.env, HX4S_CLIENT_JS_DIR: path.join(dir, 'client') },
    });
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(JSON.parse(result.stdout), ['client', 'runtime stub']);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
