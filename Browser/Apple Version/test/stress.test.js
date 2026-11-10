import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fixed, counted, free, MAX_COUNT } from '../js/event.js';
import { encode, decode } from '../js/codec.js';
import { suggest, seeded } from '../js/placer.js';
import { segmentsForDay, placeSegments } from '../js/layout.js';
import { wrap } from '../js/blocktext.js';

const UNUSUAL_NAMES = [
  'Tab\there',
  'New\nline',
  'Back\\slash',
  'Windows\r\nline',
  'Emoji 😀 test',
  'x'.repeat(500),
  '  padded  ',
  'עברית RTL',
  'Quote " and \'',
  '<b>not html</b>',
  'Ünïcödé ñ',
];

test('unusual names and places survive saving and loading', () => {
  for (const name of UNUSUAL_NAMES) {
    const event = fixed({ name, location: 'Room\t5\nFloor 2', day: 'MONDAY', startMinute: 600, durationMinutes: 60 });
    const loaded = decode(encode([event]));
    assert.equal(loaded.length, 1, `one event back for ${JSON.stringify(name)}`);
    assert.equal(loaded[0].name, event.name);
    assert.equal(loaded[0].location, event.location);
  }
});

test('many unusual events saved together all come back', () => {
  const events = UNUSUAL_NAMES.map(name => fixed({ name, day: 'TUESDAY', startMinute: 60, durationMinutes: 30 }));
  assert.deepEqual(decode(encode(events)).map(event => event.name), events.map(event => event.name));
});

test('the limits are exact: the largest allowed values work and one past them is refused', () => {
  assert.equal(counted({ name: 'a', day: 'MONDAY', startMinute: 0, durationMinutes: 60, count: MAX_COUNT }).count, MAX_COUNT);
  assert.throws(() => counted({ name: 'a', day: 'MONDAY', startMinute: 0, durationMinutes: 60, count: MAX_COUNT + 1 }));
  assert.throws(() => counted({ name: 'a', day: 'MONDAY', startMinute: 0, durationMinutes: 60, count: 0 }));
  assert.equal(fixed({ name: 'a', day: 'MONDAY', startMinute: 0, durationMinutes: 1440 }).durationMinutes, 1440);
  assert.throws(() => fixed({ name: 'a', day: 'MONDAY', startMinute: 0, durationMinutes: 1441 }));
  // The 15 minute rule applies to a start and end given by a person (events loaded from storage may be shorter).
  assert.equal(fixed({ name: 'a', day: 'MONDAY', startMinute: 0, endMinute: 15 }).durationMinutes, 15);
  assert.throws(() => fixed({ name: 'a', day: 'MONDAY', startMinute: 0, endMinute: 14 }));
  assert.throws(() => fixed({ name: '   ', day: 'MONDAY', startMinute: 0, durationMinutes: 60 }));
});

test('a day with 300 events at the same time still lays out every one', () => {
  const events = Array.from({ length: 300 }, (_, i) => fixed({ name: `E${i}`, day: 'MONDAY', startMinute: 600, durationMinutes: 60 }));
  assert.equal(placeSegments(segmentsForDay(events, 'MONDAY')).length, 300);
});

test('placing 150 free events never overlaps and reports what did not fit', () => {
  const events = Array.from({ length: 150 }, (_, i) => free({ name: `F${i}`, durationMinutes: 60 + (i % 5) * 15 }));
  const { placed, unfitted } = suggest(events, 0, 1440, seeded(1));
  assert.equal(placed.length + unfitted.length, 150);
  const byDay = {};
  for (const event of placed) {
    (byDay[event.day] ??= []).push(event);
  }
  for (const list of Object.values(byDay)) {
    list.sort((a, b) => a.startMinute - b.startMinute);
    for (let i = 1; i < list.length; i++) {
      assert.ok(list[i].startMinute >= list[i - 1].startMinute + list[i - 1].durationMinutes, 'no two placed events overlap');
    }
  }
});

test('a name with no spaces and a name of 500 letters wrap into a few short lines', () => {
  const measure = text => text.length * 8;
  for (const name of ['x'.repeat(500), 'word '.repeat(100), '😀'.repeat(60)]) {
    const lines = wrap(name, 120, 3, measure);
    assert.ok(lines.length >= 1 && lines.length <= 3);
    assert.ok(lines.every(line => measure(line) <= 120), 'every line fits the block');
  }
});
