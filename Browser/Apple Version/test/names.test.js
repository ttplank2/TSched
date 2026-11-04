import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ColorNames, resolve, encodeNames, decodeNames } from '../js/names.js';
import { COLORS } from '../js/colors.js';

function fakeStorage() {
  const data = new Map();
  return {
    getItem: key => (data.has(key) ? data.get(key) : null),
    setItem: (key, value) => data.set(key, String(value)),
  };
}

test('a color starts with its own name', () => {
  const names = new ColorNames(fakeStorage());
  assert.equal(names.nameOf(COLORS[0]), 'Red');
  assert.equal(names.hasCustomName(COLORS[0]), false);
});

test('renaming gives a color a name of its own, and it is kept after reopening', () => {
  const storage = fakeStorage();
  const names = new ColorNames(storage);
  assert.equal(names.renameAll({ [COLORS[1]]: 'ECE', [COLORS[6]]: ' Physics ' }), true);
  assert.equal(names.nameOf(COLORS[1]), 'ECE');
  assert.equal(names.nameOf(COLORS[6]), 'Physics');
  assert.equal(names.hasCustomName(COLORS[1]), true);
  const again = new ColorNames(storage);
  assert.equal(again.nameOf(COLORS[1]), 'ECE');
  assert.equal(again.nameOf(COLORS[0]), 'Red');
});

test('a blank name goes back to the original', () => {
  const names = new ColorNames(fakeStorage());
  names.renameAll({ [COLORS[1]]: 'ECE' });
  names.renameAll({ [COLORS[1]]: '   ' });
  assert.equal(names.nameOf(COLORS[1]), 'Orange');
  assert.equal(names.hasCustomName(COLORS[1]), false);
});

test('typing the original name again counts as no custom name', () => {
  const names = new ColorNames(fakeStorage());
  names.renameAll({ [COLORS[1]]: 'Orange' });
  assert.equal(names.hasCustomName(COLORS[1]), false);
});

test('colors that were not typed about keep their names', () => {
  const names = new ColorNames(fakeStorage());
  names.renameAll({ [COLORS[1]]: 'ECE' });
  names.renameAll({ [COLORS[2]]: 'Lab' });
  assert.equal(names.nameOf(COLORS[1]), 'ECE');
  assert.equal(names.nameOf(COLORS[2]), 'Lab');
});

test('resolve trims what was typed, or uses the original when it is blank', () => {
  assert.equal(resolve('  Math ', 'Red'), 'Math');
  assert.equal(resolve('', 'Red'), 'Red');
  assert.equal(resolve(null, 'Red'), 'Red');
});

test('names survive being written and read back, tabs and newlines included', () => {
  const map = new Map([[COLORS[0], 'A\tB\nC\\D'], [COLORS[3], 'Plain']]);
  assert.deepEqual(decodeNames(encodeNames(map)), map);
});

test('a damaged line is skipped, and an old darker shade is read as its current one', () => {
  const text = 'garbage\nff66bb6a\tGreeny\nzz\tbad\n';
  assert.deepEqual(decodeNames(text), new Map([[0xFF68BC6C, 'Greeny']]));
});

test('the saved text matches the Android format: hex color, a tab, the name', () => {
  assert.equal(encodeNames(new Map([[0xFFEF5350, 'Math']])), 'ffef5350\tMath\n');
});

test('saving says so when the browser refuses, and nothing changes', () => {
  const full = { getItem: () => null, setItem: () => { throw new Error('quota'); } };
  const names = new ColorNames(full);
  assert.equal(names.renameAll({ [COLORS[1]]: 'ECE' }), false);
  assert.equal(names.nameOf(COLORS[1]), 'Orange');
});
