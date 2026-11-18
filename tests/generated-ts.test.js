import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import ts from 'typescript';

const root = fileURLToPath(new URL('../', import.meta.url));
// Generated files with export names that could collide, written by npm's pretest via sbt codegen/Test/runMain hx4s.codegen.TsFixtures
const fixtures = path.join(root, 'target/ts-fixtures');

const dataUrl = (source) => `data:text/javascript,${encodeURIComponent(source)}`;

/** Converts generated TS to JS and loads it with honox/factory and the virtual modules replaced by stubs */
async function load(file, stubs) {
  const source = await readFile(path.join(fixtures, file), 'utf8');
  const output = ts.transpileModule(source, {
    reportDiagnostics: true, fileName: file,
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.Preserve },
  });
  assert.deepEqual(output.diagnostics.map((d) => ts.flattenDiagnosticMessageText(d.messageText, '\n')), [], `${file} must be valid TypeScript`);
  let code = output.outputText;
  for (const [specifier, stub] of Object.entries(stubs)) code = code.replaceAll(`"${specifier}"`, JSON.stringify(dataUrl(stub)));
  return import(dataUrl(code));
}

/** A module exporting each export name as a function that returns its own name when called */
function exportsNamed(names) {
  return [...new Set(names)].map((name, index) => `const h${index} = (c) => ${JSON.stringify(name)} + ":" + c;\nexport { h${index} as ${name} };`).join('\n');
}

test('even with export names createRoute, POST, GET or reserved words, generated routes are syntactically valid and each method calls its handler', async () => {
  const manifest = JSON.parse(await readFile(path.join(fixtures, 'manifest.json'), 'utf8'));
  const routes = manifest.filter((entry) => entry.handlers);
  assert.ok(routes.some((entry) => entry.handlers.some((h) => h.exportName === 'createRoute')));
  for (const entry of routes) {
    const module = await load(entry.file, {
      'honox/factory': 'export const createRoute = (handler) => handler;',
      'scalajs:server.js': exportsNamed(entry.handlers.map((h) => h.exportName)),
    });
    for (const { method, exportName } of entry.handlers) {
      const handler = method === 'GET' ? module.default : module[method];
      assert.equal(typeof handler, 'function', `${entry.file} ${method}`);
      assert.equal(handler('ctx'), `${exportName}:ctx`, `${entry.file} ${method}`);
    }
  }
});

test('even with export names default or createRoute, generated islands re-export the client export as the default export', async () => {
  const manifest = JSON.parse(await readFile(path.join(fixtures, 'manifest.json'), 'utf8'));
  for (const entry of manifest.filter((item) => !item.handlers)) {
    const module = await load(entry.file, { 'scalajs:client.js': exportsNamed([entry.exportName]) });
    assert.equal(module.default('x'), `${entry.exportName}:x`, entry.file);
  }
});
