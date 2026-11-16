import assert from 'node:assert/strict';
import test from 'node:test';
import { findProblems } from '../scripts/check-independence.mjs';

test('hx4s のファイルと examples は移動元のプロジェクトに依存しない', () => {
  assert.deepEqual(findProblems(), []);
});
