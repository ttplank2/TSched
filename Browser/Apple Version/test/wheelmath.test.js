import { test } from 'node:test';
import assert from 'node:assert/strict';
import { shapeRow, wrapIndex, nearestRow, startRowFor, ROW_HEIGHT, DRUM_STEP_DEGREES } from '../js/wheelmath.js';
import { COLORS } from '../js/colors.js';

test('the row in the middle is flat, full size and fully visible', () => {
  const s = shapeRow(0);
  assert.equal(s.translateY, 0);
  assert.equal(s.scaleY, 1);
  assert.equal(s.scaleX, 1);
  assert.equal(s.opacity, 1);
});

test('rows further from the middle flatten, shrink and fade', () => {
  const one = shapeRow(1);
  const two = shapeRow(2);
  assert.ok(one.scaleY < 1 && two.scaleY < one.scaleY);
  assert.ok(one.opacity < 1 && two.opacity < one.opacity);
  assert.ok(two.scaleX < one.scaleX && one.scaleX < 1);
  assert.equal(shapeRow(1).opacity, shapeRow(-1).opacity);
});

test('a row is pulled in toward the middle, below and above alike', () => {
  const below = shapeRow(1);
  const above = shapeRow(-1);
  assert.ok(below.translateY < 0, 'a row below the middle moves up');
  assert.ok(above.translateY > 0, 'a row above the middle moves down');
  assert.ok(Math.abs(below.translateY + above.translateY) < 1e-9);
});

test('the same drum formula as the Android app: 25 degrees a row, 48 pixels tall', () => {
  assert.equal(ROW_HEIGHT, 48);
  assert.equal(DRUM_STEP_DEGREES, 25);
  const radius = ROW_HEIGHT / (25 * Math.PI / 180);
  const angle = 25 * Math.PI / 180;
  assert.ok(Math.abs(shapeRow(1).translateY - (radius * Math.sin(angle) - ROW_HEIGHT)) < 1e-9);
  assert.ok(Math.abs(shapeRow(1).opacity - Math.cos(angle)) < 1e-9);
});

test('a row far from the middle is cut off at a quarter turn', () => {
  assert.ok(Math.abs(shapeRow(10).opacity) < 1e-9);
  assert.equal(shapeRow(10).opacity, shapeRow(20).opacity);
});

test('the wheel repeats: indexes wrap around the ten colors in both directions', () => {
  assert.equal(wrapIndex(0, 10), 0);
  assert.equal(wrapIndex(10, 10), 0);
  assert.equal(wrapIndex(13, 10), 3);
  assert.equal(wrapIndex(-1, 10), 9);
  assert.equal(wrapIndex(-11, 10), 9);
});

test('the wheel settles on the nearest row', () => {
  assert.equal(nearestRow(3.2), 3);
  assert.equal(nearestRow(3.5), 4);
  assert.equal(nearestRow(-0.6), -1);
  assert.equal(nearestRow(7), 7);
});

test('the wheel opens on the chosen color, else on the accent, else the first', () => {
  assert.equal(startRowFor(COLORS[6], COLORS[1]), 6);
  assert.equal(startRowFor(null, COLORS[1]), 1);
  assert.equal(startRowFor(0xFF123456, COLORS[1]), 1);
  assert.equal(startRowFor(null, 0xFF123456), 0);
});
