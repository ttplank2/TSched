import { test } from 'node:test';
import assert from 'node:assert/strict';
import { dayOfPage, nearestPage, swipeIntent, settlePage } from '../js/pager.js';

test('pages wrap from Saturday back to Sunday, in both directions', () => {
  assert.equal(dayOfPage(6), 6);
  assert.equal(dayOfPage(7), 0);
  assert.equal(dayOfPage(-1), 6);
  assert.equal(dayOfPage(-8), 6);
});

test('snapping to a day takes the fewest swipes', () => {
  assert.equal(nearestPage(0, 6), -1);
  assert.equal(nearestPage(0, 3), 3);
  assert.equal(nearestPage(0, 4), -3);
  assert.equal(nearestPage(70, 2), 72);
  assert.equal(dayOfPage(nearestPage(1003, 5)), 5);
});

test('a drag is sideways only when it is more sideways than vertical', () => {
  assert.equal(swipeIntent(3, 2, 8), 'undecided');
  assert.equal(swipeIntent(20, 5, 8), 'sideways');
  assert.equal(swipeIntent(-20, 19, 8), 'sideways');
  assert.equal(swipeIntent(20, 20, 8), 'vertical');
  assert.equal(swipeIntent(10, 30, 8), 'vertical');
  assert.equal(swipeIntent(2, -30, 8), 'vertical');
});

test('letting go settles on the nearest page, or one further after a quick flick', () => {
  assert.equal(settlePage(3.3, 0, 3), 3);
  assert.equal(settlePage(3.6, 0, 3), 4);
  assert.equal(settlePage(3.2, 3, 3), 4);
  assert.equal(settlePage(3.8, -3, 3), 3);
  assert.equal(settlePage(5.9, 0, 3), 4);
});
