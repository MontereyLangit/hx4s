import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { mkdtemp, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { baseVersion, localVersion, root } from '../scripts/lib/version.mjs';

const json = (file) => JSON.parse(readFileSync(path.join(root, file), 'utf8'));

test('vite/package.json と package-lock.json の hx4s-vite の版は version.txt と同じで、-SNAPSHOT を使わない', () => {
  const base = baseVersion();
  assert.equal(json('vite/package.json').version, base, 'vite/package.json の version を version.txt に合わせる（docs/release.md）');
  assert.equal(json('package-lock.json').packages.vite.version, base, 'npm install --package-lock-only で package-lock.json を合わせる');
});

test('build.sbt は版を version.txt から読み、版の文字列を直接書かない', () => {
  const build = readFileSync(path.join(root, 'build.sbt'), 'utf8');
  assert.match(build, /IO\.read\(\(ThisBuild \/ baseDirectory\)\.value \/ "version\.txt"\)\.trim/);
  assert.match(build, /sys\.props\.getOrElse\("hx4s\.version", s"\$base-SNAPSHOT"\)/);
  assert.doesNotMatch(build, /ThisBuild \/ version.*"\d+\.\d+\.\d+/);
});

test('ローカル公開の版は version.txt の版から作る', () => {
  const base = baseVersion();
  assert.equal(localVersion(base, '0123456789abcdef', null), `${base}-local.g0123456789`);
  assert.equal(localVersion(base, '0123456789abcdef', '00ab12cd'), `${base}-local.g0123456789.dirty-00ab12cd`);
});

test('version.txt は X.Y.Z だけを受け付ける', async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), 'hx4s-version-'));
  try {
    const file = path.join(dir, 'version.txt');
    for (const bad of ['0.1.0-SNAPSHOT', 'v0.1.0', '0.1', '']) {
      writeFileSync(file, `${bad}\n`);
      assert.throws(() => baseVersion(file), /must contain a version/, bad);
    }
    writeFileSync(file, '1.2.3\r\n');
    assert.equal(baseVersion(file), '1.2.3');
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
