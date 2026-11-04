import { test } from 'node:test';
import assert from 'node:assert/strict';
import { HOME, ADD, stepAfter } from '../js/tourkinds.js';

test('the home tour has the eight steps of the Android app and ends with Try It Out', () => {
  assert.deepEqual(HOME.steps.map(step => step.title), [
    'Welcome to TSched', 'Your Week', 'Add Event', 'Unplaced Free Events', 'Filter', 'Snap to Day', 'Settings', 'Your Events']);
  assert.equal(HOME.lastLabel, 'Try It Out');
});

test('the add tour has six steps and ends with Done', () => {
  assert.equal(ADD.steps.length, 6);
  assert.equal(ADD.lastLabel, 'Done');
});

test('stepping stops at both ends of a tour', () => {
  assert.equal(stepAfter(ADD, 0, -1), null);
  assert.equal(stepAfter(ADD, 0, 1), 1);
  assert.equal(stepAfter(ADD, 5, 1), null);
});
