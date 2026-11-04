import { test } from 'node:test';
import assert from 'node:assert/strict';
import { segmentsForDay, placeSegments } from '../js/layout.js';
import { fixed, placedAt, free } from '../js/event.js';

const at = (name, day, start, end) => fixed({ name, day, startMinute: start, endMinute: end });

test('an event shows on its own day only', () => {
  const events = [at('A', 'MONDAY', 60, 120), at('B', 'TUESDAY', 60, 120)];
  assert.deepEqual(segmentsForDay(events, 'MONDAY').map(s => s.event.name), ['A']);
});

test('an overnight event also shows its morning on the next day', () => {
  const sleep = fixed({ name: 'Sleep', day: 'MONDAY', startMinute: 23 * 60, durationMinutes: 8 * 60 });
  const monday = segmentsForDay([sleep], 'MONDAY')[0];
  assert.equal(monday.startMinute, 23 * 60);
  assert.equal(monday.endMinute, 24 * 60);
  assert.equal(monday.continuesAfter, true);
  const tuesday = segmentsForDay([sleep], 'TUESDAY')[0];
  assert.equal(tuesday.startMinute, 0);
  assert.equal(tuesday.endMinute, 7 * 60);
  assert.equal(tuesday.continuesFrom, true);
  assert.equal(segmentsForDay([sleep], 'SATURDAY').length, 0);
});

test('Saturday night carries into Sunday', () => {
  const late = fixed({ name: 'Late', day: 'SATURDAY', startMinute: 23 * 60, durationMinutes: 120 });
  assert.equal(segmentsForDay([late], 'SUNDAY')[0].endMinute, 60);
});

test('an unplaced free event is on no day, and a placed one is on its day', () => {
  const f = free({ name: 'Study', durationMinutes: 60 });
  assert.equal(segmentsForDay([f], 'MONDAY').length, 0);
  assert.equal(segmentsForDay([placedAt(f, 'MONDAY', 600)], 'MONDAY').length, 1);
});

test('events that do not overlap each get the whole width', () => {
  const p = placeSegments(segmentsForDay([at('A', 'MONDAY', 60, 120), at('B', 'MONDAY', 120, 180)], 'MONDAY'));
  assert.deepEqual(p.map(x => [x.column, x.columnCount]), [[0, 1], [0, 1]]);
});

test('overlapping events split into side-by-side columns', () => {
  const p = placeSegments(segmentsForDay([at('A', 'MONDAY', 60, 180), at('B', 'MONDAY', 90, 150), at('C', 'MONDAY', 160, 200)], 'MONDAY'));
  const byName = Object.fromEntries(p.map(x => [x.event.name, x]));
  assert.equal(byName.A.column, 0);
  assert.equal(byName.B.column, 1);
  assert.equal(byName.C.column, 1);
  assert.equal(byName.A.columnCount, 2);
  assert.equal(byName.C.columnCount, 2);
});

test('a longer event starting at the same time goes first', () => {
  const p = placeSegments(segmentsForDay([at('Short', 'MONDAY', 60, 90), at('Long', 'MONDAY', 60, 180)], 'MONDAY'));
  assert.equal(p[0].event.name, 'Long');
  assert.equal(p[0].column, 0);
});
