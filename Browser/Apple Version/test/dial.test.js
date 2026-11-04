import { test } from 'node:test';
import assert from 'node:assert/strict';
import { angleOf, hourFromAngle, minuteFromAngle, dialPosition, to24, from24, parseTyped } from '../js/dial.js';

test('angles run clockwise from 12 at the top', () => {
  assert.equal(Math.round(angleOf(0, -1)), 0);
  assert.equal(Math.round(angleOf(1, 0)), 90);
  assert.equal(Math.round(angleOf(0, 1)), 180);
  assert.equal(Math.round(angleOf(-1, 0)), 270);
});

test('the hours sit around the dial like a clock', () => {
  assert.equal(hourFromAngle(0), 12);
  assert.equal(hourFromAngle(30), 1);
  assert.equal(hourFromAngle(90), 3);
  assert.equal(hourFromAngle(180), 6);
  assert.equal(hourFromAngle(270), 9);
  assert.equal(hourFromAngle(345), 12);
  assert.equal(hourFromAngle(14), 12);
  assert.equal(hourFromAngle(16), 1);
});

test('minutes follow the angle one minute at a time', () => {
  assert.equal(minuteFromAngle(0), 0);
  assert.equal(minuteFromAngle(90), 15);
  assert.equal(minuteFromAngle(180), 30);
  assert.equal(minuteFromAngle(270), 45);
  assert.equal(minuteFromAngle(6), 1);
  assert.equal(minuteFromAngle(359), 0);
});

test('a number on the dial is placed at the right spot around the middle', () => {
  const twelve = dialPosition(0, 100);
  assert.ok(Math.abs(twelve.x) < 1e-9 && Math.abs(twelve.y + 100) < 1e-9);
  const three = dialPosition(90, 100);
  assert.ok(Math.abs(three.x - 100) < 1e-9 && Math.abs(three.y) < 1e-9);
});

test('12 hour times become minutes after midnight and back', () => {
  assert.equal(to24(12, 'AM'), 0);
  assert.equal(to24(1, 'AM'), 1);
  assert.equal(to24(12, 'PM'), 12);
  assert.equal(to24(11, 'PM'), 23);
  assert.deepEqual(from24(0), { hour: 12, period: 'AM' });
  assert.deepEqual(from24(13), { hour: 1, period: 'PM' });
  assert.deepEqual(from24(12), { hour: 12, period: 'PM' });
});

test('typed hours and minutes are checked', () => {
  assert.equal(parseTyped('9', '5', 'AM'), 9 * 60 + 5);
  assert.equal(parseTyped('12', '00', 'AM'), 0);
  assert.equal(parseTyped('3', '45', 'PM'), 15 * 60 + 45);
  assert.equal(parseTyped('0', '30', 'AM'), null);
  assert.equal(parseTyped('13', '30', 'AM'), null);
  assert.equal(parseTyped('9', '60', 'AM'), null);
  assert.equal(parseTyped('', '30', 'AM'), null);
  assert.equal(parseTyped('9', 'x', 'AM'), null);
});
