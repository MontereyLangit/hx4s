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

test('server mode resolves the client export under another prefix and links the two Scala.js projects in order', () => {
  const plugins = hx4s({ mode: 'production', sbt: { cwd: root } });
  const scala = scalaPlugins(plugins);
  assert.equal(scala.length, 2);
  for (const plugin of scala) assert.equal(plugin.buildStart.sequential, true);
  assert.deepEqual(named(plugins, 'hx4s:client-alias').config().resolve.alias, { 'scalajs:client.js': 'scalajs-client:client.js' });
  assert.ok(plugins.flat(Infinity).some((plugin) => plugin?.name?.startsWith('honox')), 'includes the honox() plugins');
});

test('moduleIDs, prefixes and honox settings of server mode can be overridden', () => {
  const plugins = hx4s({ mode: 'production', client: { module: 'browser', uriPrefix: 'scalajs-browser' } });
  assert.deepEqual(named(plugins, 'hx4s:client-alias').config().resolve.alias, { 'scalajs:browser.js': 'scalajs-browser:browser.js' });
});

test('client mode links only the client Scala.js and outputs under static/ by default; output names can be overridden', () => {
  const plugins = hx4s({ mode: 'client', sbt: { cwd: root }, client: { entry: './app/main.ts' } });
  assert.equal(scalaPlugins(plugins).length, 1);
  assert.equal(named(plugins, 'hx4s:client-alias'), undefined);
  assert.ok(!plugins.flat(Infinity).some((plugin) => plugin?.name?.startsWith('honox')), 'the client build does not use honox()');
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

test('client: false links only the server and adds neither the client alias nor React settings; it cannot be combined with client mode', () => {
  const plugins = hx4s({ mode: 'production', sbt: { cwd: root }, client: false });
  assert.equal(scalaPlugins(plugins).length, 1);
  assert.equal(named(plugins, 'hx4s:client-alias'), undefined);
  assert.equal(named(plugins, 'hx4s:client-build'), undefined);
  assert.ok(plugins.flat(Infinity).some((plugin) => plugin?.name?.startsWith('honox')), 'includes the honox() plugins');
  assert.throws(() => hx4s({ mode: 'client', client: false }), /client: false.*--mode client/);
});

test('scalaProject serializes function-style buildStart too', () => {
  const plugin = scalaProject(root, 'client', 'scalajs-x');
  assert.equal(plugin.buildStart.sequential, true);
  assert.equal(typeof plugin.buildStart.handler, 'function');
});

test('HonoX wraps islands in React SSR and serializes props', async () => {
  const fixture = await mkdtemp(path.join(root, 'tests', '.islands-bridge-'));
  try {
    await mkdir(path.join(fixture, 'app/islands'), { recursive: true });
    // A stand-in for the linked Scala client export (not a Scala compiler test)
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
    // The same React element gets no boundary unless it goes through HonoX's island transform
    assert.equal(rawHtml, '<button>from Scala</button>');
    assert.doesNotMatch(rawHtml, /honox-island|component-name|data-serialized-props/);
  } finally {
    await rm(fixture, { recursive: true, force: true });
  }
});
