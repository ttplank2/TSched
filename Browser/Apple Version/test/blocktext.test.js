import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fit, nameLinesFor, wrap } from '../js/blocktext.js';

/** Every character is 10 wide, so widths are easy to work out by hand. */
const width = text => text.length * 10;

test('events under 30 minutes show no text', () => {
  assert.deepEqual(fit(29, 100, 16, 14), { nameLines: 0, showTime: false });
});

test('a one-hour event gets up to two name lines, longer ones up to three, shorter one', () => {
  assert.equal(nameLinesFor(45), 1);
  assert.equal(nameLinesFor(60), 2);
  assert.equal(nameLinesFor(61), 3);
});

test('text height is shared first name line, then time, then more name lines', () => {
  assert.deepEqual(fit(60, 16, 16, 14), { nameLines: 1, showTime: false });
  assert.deepEqual(fit(60, 30, 16, 14), { nameLines: 1, showTime: true });
  assert.deepEqual(fit(60, 46, 16, 14), { nameLines: 2, showTime: true });
  assert.deepEqual(fit(180, 100, 16, 14), { nameLines: 3, showTime: true });
});

test('names wrap between words', () => {
  assert.deepEqual(wrap('MATH Lecture Hall', 80, 3, width), ['MATH', 'Lecture', 'Hall']);
  assert.deepEqual(wrap('a b c', 100, 3, width), ['a b c']);
});

test('a name with too many words ends with an ellipsis', () => {
  const lines = wrap('one two three four five six', 70, 2, width);
  assert.equal(lines.length, 2);
  assert.ok(lines[1].endsWith('...'));
});

test('a word wider than the line carries on onto the next line', () => {
  assert.deepEqual(wrap('Extraordinarily', 80, 2, width), ['Extraord', 'inarily']);
});

test('a word wider than the line with only one line is cut with an ellipsis', () => {
  assert.deepEqual(wrap('Extraordinarily', 80, 1, width), ['Extra...']);
});

test('a long word that still does not fit in the lines there are ends with an ellipsis', () => {
  assert.deepEqual(wrap('Aaaaaaaaaaaaaaaaaaaaaaaa', 80, 2, width), ['Aaaaaaaa', 'aaaaa...']);
});

test('a long word after other words starts on its own line and carries on', () => {
  assert.deepEqual(wrap('Go Extraordinarily', 80, 3, width), ['Go', 'Extraord', 'inarily']);
});

test('wrap always returns a line', () => {
  assert.deepEqual(wrap('   ', 100, 2, width), ['']);
});
