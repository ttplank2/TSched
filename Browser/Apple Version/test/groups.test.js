import { test } from 'node:test';
import assert from 'node:assert/strict';
import { saveFixedGroup, saveFreeGroup, daysOf, membersOf } from '../js/groups.js';
import { fixed, counted, free, placedAt, withColor } from '../js/event.js';

const details = (fields = {}) => fixed({ name: 'Gym', day: 'MONDAY', startMinute: 600, durationMinutes: 60, ...fields });

test('saving on several days makes one grouped event per day', () => {
  const saved = saveFixedGroup([], details(), ['MONDAY', 'WEDNESDAY']);
  assert.equal(saved.length, 2);
  assert.equal(new Set(saved.map(event => event.groupId)).size, 1);
  assert.notEqual(saved[0].groupId, '');
  assert.deepEqual(daysOf(saved), ['MONDAY', 'WEDNESDAY']);
});

test('editing keeps the ids of days that stay, adds new days and drops days that were unticked', () => {
  const first = saveFixedGroup([], details(), ['MONDAY', 'TUESDAY']);
  const mondayId = first.find(event => event.day === 'MONDAY').id;
  const edit = { ...details({ name: 'Gym 2' }), id: mondayId };
  const second = saveFixedGroup(first, edit, ['MONDAY', 'FRIDAY']);
  assert.deepEqual(second.map(event => event.day).sort(), ['FRIDAY', 'MONDAY']);
  assert.equal(second.find(event => event.day === 'MONDAY').id, mondayId);
  assert.ok(second.every(event => event.name === 'Gym 2'));
  assert.equal(new Set(second.map(event => event.groupId)).size, 1);
});

test('other events are untouched by an edit', () => {
  const other = details({ name: 'Lab', day: 'SUNDAY' });
  const saved = saveFixedGroup([other], details(), ['MONDAY']);
  assert.ok(saved.includes(other));
});

test('a counted event keeps what each day has left unless the count is changed', () => {
  const base = counted({ name: 'Lecture', day: 'MONDAY', startMinute: 600, durationMinutes: 60, count: 10 });
  const used = { ...base, count: 4 };
  const edit = { ...base, name: 'Lecture 2' };
  assert.equal(saveFixedGroup([used], edit, ['MONDAY'], true)[0].count, 4);
  assert.equal(saveFixedGroup([used], edit, ['MONDAY'], false)[0].count, 10);
});

test('no days is refused for a fixed event', () => {
  assert.throws(() => saveFixedGroup([], details(), []), /Pick a day/);
});

test('a free event on several days is one copy per day, each limited to its own day', () => {
  const saved = saveFreeGroup([], withColor(free({ name: 'Read', durationMinutes: 60 }), 0xff00ff00), ['TUESDAY', 'THURSDAY']);
  assert.equal(saved.length, 2);
  assert.deepEqual(saved.map(event => event.allowedDays), [['TUESDAY'], ['THURSDAY']]);
  assert.equal(saved[0].color, 0xff00ff00);
});

test('a free event with no days may go on any day', () => {
  const saved = saveFreeGroup([], free({ name: 'Read', durationMinutes: 60 }), []);
  assert.equal(saved.length, 1);
  assert.deepEqual(saved[0].allowedDays, []);
});

test('editing a placed free event keeps its place on a day still chosen, and loses it otherwise', () => {
  const placed = placedAt(free({ name: 'Read', durationMinutes: 60, allowedDays: ['TUESDAY'] }), 'TUESDAY', 600);
  const edit = { ...placed, name: 'Read more' };
  const kept = saveFreeGroup([placed], edit, ['TUESDAY'])[0];
  assert.equal(kept.day, 'TUESDAY');
  assert.equal(kept.startMinute, 600);
  const lost = saveFreeGroup([placed], edit, ['WEDNESDAY'])[0];
  assert.equal(lost.day, null);
});

test('members of a group are found together', () => {
  const saved = saveFixedGroup([], details(), ['MONDAY', 'TUESDAY']);
  assert.equal(membersOf(saved, saved[0]).length, 2);
});
