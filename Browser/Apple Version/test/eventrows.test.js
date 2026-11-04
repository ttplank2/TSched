import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildRows, unplacedRows, placementBlocks, dayList } from '../js/eventrows.js';
import { fixed, free, withGroupId, placedAt } from '../js/event.js';

const at = (name, day, start, group) => {
  const event = fixed({ name, day, startMinute: start, durationMinutes: 60 });
  return group ? withGroupId(event, group) : event;
};

test('a group of days is one row, standing for its first day of the week', () => {
  const events = [at('Gym', 'WEDNESDAY', 600, 'g'), at('Lab', 'MONDAY', 600), at('Gym', 'MONDAY', 600, 'g')];
  const rows = buildRows(events);
  assert.equal(rows.length, 2);
  const gym = rows.find(row => row.first.name === 'Gym');
  assert.equal(gym.members.length, 2);
  assert.equal(gym.first.day, 'MONDAY');
});

test('rows go in week order, then by start time and name', () => {
  const rows = buildRows([at('B', 'TUESDAY', 600), at('A', 'TUESDAY', 600), at('C', 'SUNDAY', 900)]);
  assert.deepEqual(rows.map(row => row.first.name), ['C', 'A', 'B']);
});

test('unplaced rows hold only free events with no day', () => {
  const waiting = free({ name: 'Read', durationMinutes: 60 });
  const onCalendar = placedAt(free({ name: 'Run', durationMinutes: 60 }), 'MONDAY', 600);
  const rows = unplacedRows([at('Class', 'MONDAY', 600), waiting, onCalendar]);
  assert.deepEqual(rows.map(row => row.first.name), ['Read']);
});

test('placement blocks put a group together, sorted by name', () => {
  const a = withGroupId(placedAt(free({ name: 'Zed', durationMinutes: 60 }), 'FRIDAY', 600), 'z');
  const b = withGroupId(placedAt(free({ name: 'Zed', durationMinutes: 60 }), 'MONDAY', 600), 'z');
  const c = placedAt(free({ name: 'Alpha', durationMinutes: 60 }), 'TUESDAY', 600);
  const blocks = placementBlocks([a, b, c]);
  assert.deepEqual(blocks.map(block => block.map(e => e.day)), [['TUESDAY'], ['MONDAY', 'FRIDAY']]);
});

test('day lists are Sunday first', () => {
  assert.equal(dayList(['FRIDAY', 'SUNDAY', 'TUESDAY']), 'Sun · Tues · Fri');
});
