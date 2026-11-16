import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import ts from 'typescript';

const root = fileURLToPath(new URL('../', import.meta.url));
// npm の pretest が sbt codegen/Test/runMain hx4s.codegen.TsFixtures で書き出す、衝突しうる export 名の生成ファイル
const fixtures = path.join(root, 'target/ts-fixtures');

const dataUrl = (source) => `data:text/javascript,${encodeURIComponent(source)}`;

/** 生成 TS を JS に変換し、honox/factory と仮想モジュールをスタブに差し替えて読み込む */
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

/** 各 export 名を、呼ばれると自分の名前を返す関数として公開するモジュール */
function exportsNamed(names) {
  return [...new Set(names)].map((name, index) => `const h${index} = (c) => ${JSON.stringify(name)} + ":" + c;\nexport { h${index} as ${name} };`).join('\n');
}

test('export 名が createRoute・POST・GET・予約語でも、生成 route は構文が正しく、各メソッドが対応するハンドラを呼ぶ', async () => {
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

test('export 名が default や createRoute でも、生成 island は client の export を default export に再公開する', async () => {
  const manifest = JSON.parse(await readFile(path.join(fixtures, 'manifest.json'), 'utf8'));
  for (const entry of manifest.filter((item) => !item.handlers)) {
    const module = await load(entry.file, { 'scalajs:client.js': exportsNamed([entry.exportName]) });
    assert.equal(module.default('x'), `${entry.exportName}:x`, entry.file);
  }
});
