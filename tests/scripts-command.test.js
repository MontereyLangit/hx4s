import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { quoteForCmd, spawnBatch, spawnTool } from '../scripts/lib/command.mjs';

const windows = process.platform === 'win32';

async function withSpacedDir(body) {
  const dir = await mkdtemp(path.join(os.tmpdir(), 'hx4s space & dir '));
  try {
    return await body(dir);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

test('quoteForCmd rejects characters whose meaning changes in cmd.exe, and quotes with a trailing \\ doubled', () => {
  assert.equal(quoteForCmd('a b & c'), '"a b & c"');
  assert.equal(quoteForCmd('C:\\dir with space\\'), '"C:\\dir with space\\\\"');
  for (const arg of ['say "hi"', '%PATH%', 'wow!', 'a^b', 'two\nlines']) assert.throws(() => quoteForCmd(arg), /cmd\.exe/);
});

test('npm starts without a shell even in a working directory containing spaces and &', async () => {
  await withSpacedDir(async (dir) => {
    const result = spawnTool('npm', ['--version'], { cwd: dir, encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout.trim(), /^\d+\.\d+\.\d+/);
  });
});

test('a Windows batch file placed at a path containing spaces and & receives arguments containing spaces and & as-is', { skip: !windows && 'Windows only' }, async () => {
  await withSpacedDir(async (dir) => {
    const script = path.join(dir, 'print args.cmd');
    await writeFile(script, `@"${process.execPath}" -e "console.log(JSON.stringify(process.argv.slice(1)))" %*\r\n`);
    const args = ['plain', 'with space', 'a&b', 'x|y', 'C:\\trailing\\'];
    const result = spawnBatch(script, args, { cwd: dir, encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(JSON.parse(result.stdout), args);
  });
});
