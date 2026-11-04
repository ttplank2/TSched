import { test } from 'node:test';
import assert from 'node:assert/strict';
import { clock, range, hourLabels, length, freeCountLabel } from '../js/time.js';

test('clock formats minutes after midnight', () => {
  assert.equal(clock(0), '12:00 AM');
  assert.equal(clock(870), '2:30 PM');
  assert.equal(clock(720), '12:00 PM');
  assert.equal(clock(1440), '12:00 AM');
  assert.equal(clock(65), '1:05 AM');
});

test('range joins two clocks with an en dash', () => {
  assert.equal(range(540, 590), '9:00 AM – 9:50 AM');
});

test('hour labels run from 12 AM to 11 PM', () => {
  const labels = hourLabels();
  assert.equal(labels.length, 24);
  assert.equal(labels[0], '12 AM');
  assert.equal(labels[12], '12 PM');
  assert.equal(labels[23], '11 PM');
});

test('length reads as hours and minutes', () => {
  assert.equal(length(90), '1h 30m');
  assert.equal(length(120), '2h');
  assert.equal(length(45), '45m');
});

test('the unplaced count stops at 9+', () => {
  assert.equal(freeCountLabel(0), '0');
  assert.equal(freeCountLabel(9), '9');
  assert.equal(freeCountLabel(10), '9+');
});
