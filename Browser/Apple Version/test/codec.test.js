import { test } from 'node:test';
import assert from 'node:assert/strict';
import { encode, decode } from '../js/codec.js';
import { fixed, free, counted, placedAt, withColor, withGroupId } from '../js/event.js';

test('events survive being written and read back', () => {
  const events = [
    withColor(fixed({ name: 'Math', location: 'CIF', day: 'MONDAY', startMinute: 540, endMinute: 590 }), 0xFF5AB1F6),
    placedAt(free({ name: 'Study', durationMinutes: 90, allowedDays: ['TUESDAY', 'FRIDAY'] }), 'FRIDAY', 600),
    free({ name: 'Read', durationMinutes: 30 }),
    counted({ name: 'Lab', day: 'THURSDAY', startMinute: 600, endMinute: 700, count: 7 }),
    withGroupId(fixed({ name: 'Lunch', day: 'MONDAY', startMinute: 700, endMinute: 760 }), 'g1'),
  ];
  assert.deepEqual(decode(encode(events)), events);
});

test('tabs, newlines and backslashes inside names are kept', () => {
  const e = fixed({ name: 'A\tB\nC\\D', location: 'x\ty', day: 'MONDAY', startMinute: 60, endMinute: 120 });
  assert.deepEqual(decode(encode([e])), [e]);
});

test('a damaged line is skipped without losing the others', () => {
  const good = fixed({ name: 'Good', day: 'MONDAY', startMinute: 60, endMinute: 120 });
  const text = 'garbage line\n' + encode([good]) + 'a\tb\n';
  assert.deepEqual(decode(text), [good]);
});

test('empty text is no events', () => {
  assert.deepEqual(decode(''), []);
});

test('the saved text has the same twelve tab-separated fields the Android app uses', () => {
  const e = fixed({ name: 'Math', location: 'CIF', day: 'MONDAY', startMinute: 540, endMinute: 590 });
  const fields = encode([e]).trimEnd().split('\t');
  assert.equal(fields.length, 12);
  assert.equal(fields[1], 'FIXED');
  assert.equal(fields[4], 'MONDAY');
  assert.equal(fields[5], '540');
  assert.equal(fields[6], '50');
  assert.equal(fields[9], 'ffffc93c');
});

test('a color taken off the wheel is read back as its replacement', () => {
  const line = ['id1', 'FIXED', 'Old', '', 'MONDAY', '60', '60', '-', '-', 'ff66bb6a', '', '0'].join('\t');
  assert.equal(decode(line + '\n')[0].color, 0xFF68BC6C);
});
