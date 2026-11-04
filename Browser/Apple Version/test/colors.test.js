import { test } from 'node:test';
import assert from 'node:assert/strict';
import { COLORS, NAMES, DEFAULT, nameOf, onWheel, currentShade, hex } from '../js/colors.js';

test('there are ten colors with ten names, and yellow is the default', () => {
  assert.equal(COLORS.length, 10);
  assert.equal(NAMES.length, 10);
  assert.equal(DEFAULT, 0xFFFFC93C);
  assert.equal(nameOf(DEFAULT), 'Yellow');
  assert.equal(nameOf(0xFFEF5350), 'Red');
  assert.equal(nameOf(0xFF123456), 'Custom');
});

test('darker old shades become the current brighter ones', () => {
  assert.equal(currentShade(0xFF66BB6A), 0xFF68BC6C);
  assert.equal(currentShade(0xFFAB47BC), 0xFFCF96D9);
  assert.equal(currentShade(0xFFEF5350), 0xFFEF5350);
});

test('colors taken off the wheel become their replacements, and unknown ones the default', () => {
  assert.equal(onWheel(0xFF4DD0E1), 0xFF2BBDAF);
  assert.equal(onWheel(0xFF5C6BC0), 0xFFCF96D9);
  assert.equal(onWheel(0xFF123456), DEFAULT);
  assert.equal(onWheel(0xFF5AB1F6), 0xFF5AB1F6);
});

test('hex turns a color into a CSS color', () => {
  assert.equal(hex(0xFFFFC93C), '#ffc93c');
  assert.equal(hex(0xFF0A0B0C), '#0a0b0c');
});
