import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import test from 'node:test';
import { build } from 'vite';
import { islandComponents } from 'honox/vite';
import hx4s, { scalaProject } from '../vite/dist/index.js';

const root = path.resolve(fileURLToPath(new URL('..', import.meta.url)));

const scalaPlugins = (plugins) => plugins.flat(Infinity).filter((plugin) => plugin?.name === 'scalajs:sbt-scalajs-plugin');
const named = (plugins, name) => plugins.flat(Infinity).find((plugin) => plugin?.name === name);

test('server モードは client の export を別の接頭辞で解決し、2 つの Scala.js プロジェクトを順にリンクする', () => {
  const plugins = hx4s({ mode: 'production', sbt: { cwd: root } });
  const scala = scalaPlugins(plugins);
  assert.equal(scala.length, 2);
  for (const plugin of scala) assert.equal(plugin.buildStart.sequential, true);
  assert.deepEqual(named(plugins, 'hx4s:client-alias').config().resolve.alias, { 'scalajs:client.js': 'scalajs-client:client.js' });
  assert.ok(plugins.flat(Infinity).some((plugin) => plugin?.name?.startsWith('honox')), 'honox() のプラグインを含む');
});

test('server モードの moduleID・接頭辞・honox の設定は上書きできる', () => {
  const plugins = hx4s({ mode: 'production', client: { module: 'browser', uriPrefix: 'scalajs-browser' } });
  assert.deepEqual(named(plugins, 'hx4s:client-alias').config().resolve.alias, { 'scalajs:browser.js': 'scalajs-browser:browser.js' });
});

test('client モードは client の Scala.js だけをリンクし、既定では static/ 配下へ出す。出力名は上書きできる', () => {
  const plugins = hx4s({ mode: 'client', sbt: { cwd: root }, client: { entry: './app/main.ts' } });
  assert.equal(scalaPlugins(plugins).length, 1);
  assert.equal(named(plugins, 'hx4s:client-alias'), undefined);
  assert.ok(!plugins.flat(Infinity).some((plugin) => plugin?.name?.startsWith('honox')), 'client ビルドは honox() を使わない');
  const build = named(plugins, 'hx4s:client-build').config().build;
  assert.deepEqual(build.rollupOptions.input, ['./app/main.ts']);
  assert.equal(build.rollupOptions.output.entryFileNames, 'static/client.js');
  assert.equal(build.emptyOutDir, false);
  assert.deepEqual(named(hx4s({ mode: 'client' }), 'hx4s:client-build').config().build.rollupOptions.input, ['./app/client.ts']);
  const renamed = named(hx4s({ mode: 'client', client: { output: { entryFileNames: 'static/client-[hash].js' } } }), 'hx4s:client-build');
  assert.deepEqual(renamed.config().build.rollupOptions.output, {
    entryFileNames: 'static/client-[hash].js', chunkFileNames: 'static/assets/[name]-[hash].js', assetFileNames: 'static/assets/[name].[ext]',
  });
});

test('client: false は server だけをリンクし、client の別名と React の設定を足さない。client モードとは組み合わせられない', () => {
  const plugins = hx4s({ mode: 'production', sbt: { cwd: root }, client: false });
  assert.equal(scalaPlugins(plugins).length, 1);
  assert.equal(named(plugins, 'hx4s:client-alias'), undefined);
  assert.equal(named(plugins, 'hx4s:client-build'), undefined);
  assert.ok(plugins.flat(Infinity).some((plugin) => plugin?.name?.startsWith('honox')), 'honox() のプラグインを含む');
  assert.throws(() => hx4s({ mode: 'client', client: false }), /client: false.*--mode client/);
});

test('scalaProject は関数形式の buildStart も直列にする', () => {
  const plugin = scalaProject(root, 'client', 'scalajs-x');
  assert.equal(plugin.buildStart.sequential, true);
  assert.equal(typeof plugin.buildStart.handler, 'function');
});

test('HonoX は island を React SSR で包み、props をシリアライズする', async () => {
  const fixture = await mkdtemp(path.join(root, 'tests', '.islands-bridge-'));
  try {
    await mkdir(path.join(fixture, 'app/islands'), { recursive: true });
    // リンク済みの Scala の client export の代わり（Scala コンパイラのテストではない）
    await writeFile(path.join(fixture, 'client.js'), `import { createElement } from 'react';
export const Probe = ({ label }) => createElement('button', null, label);
`);
    await writeFile(path.join(fixture, 'app/islands/probe.tsx'), `import { Probe } from 'scalajs:client.js';
export default Probe;
`);
    await writeFile(path.join(fixture, 'entry.ts'), `import { createElement } from 'react';
import { renderToString } from 'react-dom/server';
import Probe from './app/islands/probe';
import { Probe as RawProbe } from 'scalajs:client.js';
export const html = renderToString(createElement(Probe, { label: 'from Scala' }));
export const rawHtml = renderToString(createElement(RawProbe, { label: 'from Scala' }));
`);
    await build({
      configFile: false,
      root: fixture,
      logLevel: 'silent',
      plugins: [islandComponents({ reactApiImportSource: 'react' })],
      resolve: { alias: { 'scalajs:client.js': path.join(fixture, 'client.js') } },
      esbuild: { jsx: 'automatic', jsxImportSource: 'react' },
      ssr: { noExternal: ['honox'] },
      build: {
        ssr: path.join(fixture, 'entry.ts'),
        outDir: path.join(fixture, 'out'),
        rollupOptions: { output: { entryFileNames: 'entry.mjs' } },
      },
    });
    const { html, rawHtml } = await import(pathToFileURL(path.join(fixture, 'out/entry.mjs')).href);
    assert.match(html, /<honox-island/);
    assert.match(html, /component-name="\/app\/islands\/probe.tsx"/);
    assert.match(html, /data-serialized-props="\{&quot;label&quot;:&quot;from Scala&quot;\}"/);
    assert.match(html, /<button>from Scala<\/button>/);
    // 同じ React 要素でも、HonoX の island 変換を通らなければ境界は付かない
    assert.equal(rawHtml, '<button>from Scala</button>');
    assert.doesNotMatch(rawHtml, /honox-island|component-name|data-serialized-props/);
  } finally {
    await rm(fixture, { recursive: true, force: true });
  }
});
