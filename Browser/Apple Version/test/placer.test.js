import { test } from 'node:test';
import assert from 'node:assert/strict';
import { suggest, recallAll, countPlaced, seeded } from '../js/placer.js';
import { fixed, free, placedAt, isPlaced } from '../js/event.js';

const busyFixed = (day, start, end) => fixed({ name: 'Class', day, startMinute: start, endMinute: end });
const study = (minutes, allowedDays = [], name = 'Study') => free({ name, durationMinutes: minutes, allowedDays });

test('a free event goes into open time and not on a fixed event', () => {
  const week = [busyFixed('MONDAY', 8 * 60, 12 * 60), study(60, ['MONDAY'])];
  const { placed, unfitted } = suggest(week, 8 * 60, 14 * 60, seeded(1));
  assert.equal(unfitted.length, 0);
  assert.equal(placed[0].day, 'MONDAY');
  assert.equal(placed[0].startMinute, 12 * 60);
});

test('an event stays inside the hours it is allowed', () => {
  for (let seed = 0; seed < 20; seed++) {
    const { placed } = suggest([study(90)], 9 * 60, 12 * 60, seeded(seed));
    assert.ok(placed[0].startMinute >= 9 * 60 && placed[0].startMinute + 90 <= 12 * 60);
  }
});

test('an event only goes on a day it allows', () => {
  for (let seed = 0; seed < 20; seed++) {
    const { placed } = suggest([study(60, ['TUESDAY', 'THURSDAY'])], 8 * 60, 22 * 60, seeded(seed));
    assert.ok(['TUESDAY', 'THURSDAY'].includes(placed[0].day));
  }
});

test('an event that does not fit anywhere is reported, not placed', () => {
  const week = [busyFixed('MONDAY', 8 * 60, 22 * 60), study(60, ['MONDAY'], 'Gym')];
  const { placed, unfitted } = suggest(week, 8 * 60, 22 * 60, seeded(1));
  assert.equal(placed.length, 0);
  assert.equal(unfitted[0].name, 'Gym');
});

test('placed events do not overlap each other', () => {
  const week = [study(120, ['MONDAY'], 'A'), study(120, ['MONDAY'], 'B'), study(120, ['MONDAY'], 'C')];
  const { placed } = suggest(week, 8 * 60, 22 * 60, seeded(3));
  placed.sort((a, b) => a.startMinute - b.startMinute);
  for (let i = 1; i < placed.length; i++) {
    assert.ok(placed[i].startMinute >= placed[i - 1].startMinute + placed[i - 1].durationMinutes);
  }
});

test('an overnight event keeps the start of the next day busy', () => {
  const sleep = fixed({ name: 'Sleep', day: 'MONDAY', startMinute: 22 * 60, durationMinutes: 10 * 60 });
  const { placed } = suggest([sleep, study(60, ['TUESDAY'])], 0, 12 * 60, seeded(1));
  assert.equal(placed[0].startMinute, 8 * 60);
});

test('the same seed gives the same layout and the layout can differ between seeds', () => {
  const week = [study(60, [], 'A'), study(45, [], 'B'), study(30, [], 'C')];
  const layout = seed => suggest(week, 8 * 60, 22 * 60, seeded(seed)).placed.map(e => `${e.name}${e.day}${e.startMinute}`).join();
  assert.equal(layout(5), layout(5));
  assert.ok(new Set([1, 2, 3, 4, 5, 6].map(layout)).size > 1);
});

test('a window that ends before it starts is refused', () => {
  assert.throws(() => suggest([], 600, 600), /end time must be after the start time/);
});

test('recall all takes placed free events off the calendar and leaves fixed ones', () => {
  const placed = placedAt(study(60), 'FRIDAY', 600);
  const week = [busyFixed('MONDAY', 60, 120), placed];
  assert.equal(countPlaced(week), 1);
  const recalled = recallAll(week);
  assert.equal(countPlaced(recalled), 0);
  assert.equal(recalled[0].day, 'MONDAY');
  assert.equal(isPlaced(recalled[1]), false);
});
