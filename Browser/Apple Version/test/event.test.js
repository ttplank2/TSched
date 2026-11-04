import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fixed, free, counted, placedAt, unplaced, crossesMidnight, isPlaced, endMinute, withColor } from '../js/event.js';
import { DEFAULT } from '../js/colors.js';

test('a fixed event keeps its details and the default color', () => {
  const e = fixed({ name: ' Math ', location: ' CIF ', day: 'MONDAY', startMinute: 540, endMinute: 590 });
  assert.equal(e.kind, 'FIXED');
  assert.equal(e.name, 'Math');
  assert.equal(e.location, 'CIF');
  assert.equal(e.durationMinutes, 50);
  assert.equal(endMinute(e), 590);
  assert.equal(e.color, DEFAULT);
  assert.ok(e.id.length > 10);
});

test('a fixed event is rejected for a missing name, day or bad times', () => {
  const ok = { name: 'A', day: 'MONDAY', startMinute: 540, endMinute: 600 };
  assert.throws(() => fixed({ ...ok, name: '  ' }), /needs a name/);
  assert.throws(() => fixed({ ...ok, day: null }), /Pick a day/);
  assert.throws(() => fixed({ ...ok, startMinute: -1 }), /Pick a start time/);
  assert.throws(() => fixed({ ...ok, startMinute: null }), /Pick a start time/);
  assert.throws(() => fixed({ ...ok, endMinute: 540 }), /must be after/);
  assert.throws(() => fixed({ ...ok, endMinute: 550 }), /at least 15 minutes/);
});

test('an overnight event ends the next day and crosses midnight', () => {
  const e = fixed({ name: 'Sleep', day: 'MONDAY', startMinute: 23 * 60, durationMinutes: 8 * 60 });
  assert.equal(crossesMidnight(e), true);
  assert.equal(endMinute(e), 31 * 60);
  assert.equal(crossesMidnight(fixed({ name: 'A', day: 'MONDAY', startMinute: 60, endMinute: 120 })), false);
});

test('an event can be at most 24 hours long', () => {
  assert.throws(() => fixed({ name: 'A', day: 'MONDAY', startMinute: 0, durationMinutes: 1441 }), /24 hours/);
});

test('a free event has a length, and any or every day means any day', () => {
  const e = free({ name: 'Study', durationMinutes: 60, allowedDays: ['MONDAY', 'TUESDAY'] });
  assert.equal(e.kind, 'FREE');
  assert.deepEqual(e.allowedDays, ['MONDAY', 'TUESDAY']);
  assert.equal(isPlaced(e), false);
  assert.deepEqual(free({ name: 'S', durationMinutes: 60, allowedDays: [] }).allowedDays, []);
  const all = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
  assert.deepEqual(free({ name: 'S', durationMinutes: 60, allowedDays: all }).allowedDays, []);
  assert.throws(() => free({ name: '', durationMinutes: 60 }), /needs a name/);
  assert.throws(() => free({ name: 'S', durationMinutes: 0 }), /needs a length/);
  assert.throws(() => free({ name: 'S', durationMinutes: 10 }), /at least 15 minutes/);
});

test('placing a free event gives it a day and time, and unplacing takes them away', () => {
  const e = free({ name: 'Study', durationMinutes: 60 });
  const p = placedAt(e, 'WEDNESDAY', 600);
  assert.equal(isPlaced(p), true);
  assert.equal(p.day, 'WEDNESDAY');
  assert.equal(p.startMinute, 600);
  assert.equal(p.id, e.id);
  const back = unplaced(p);
  assert.equal(isPlaced(back), false);
  assert.equal(back.day, null);
  assert.throws(() => placedAt(e, 'WEDNESDAY', 24 * 60 - 30), /fit inside one day/);
});

test('a counted event needs a count from 1 to 52', () => {
  const ok = { name: 'Class', day: 'MONDAY', startMinute: 540, endMinute: 600 };
  assert.equal(counted({ ...ok, count: 10 }).count, 10);
  assert.throws(() => counted({ ...ok, count: 0 }), /from 1 to 52/);
  assert.throws(() => counted({ ...ok, count: 53 }), /from 1 to 52/);
});

test('withColor changes only the color', () => {
  const e = fixed({ name: 'A', day: 'MONDAY', startMinute: 60, endMinute: 120 });
  const c = withColor(e, 0xFFEF5350);
  assert.equal(c.color, 0xFFEF5350);
  assert.equal(c.name, 'A');
  assert.equal(e.color, DEFAULT);
});
