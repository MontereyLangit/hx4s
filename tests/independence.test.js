import assert from 'node:assert/strict';
import test from 'node:test';
import { findProblems } from '../scripts/check-independence.mjs';

test('hx4s files and the examples do not depend on the original project', () => {
  assert.deepEqual(findProblems(), []);
});
