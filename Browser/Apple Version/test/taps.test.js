import { test } from 'node:test';
import assert from 'node:assert/strict';
import { TapOrDoubleTap, COOLDOWN_MS } from '../js/taps.js';

/** A fake clock and timer, so the tests need no waiting. */
function fakeTime() {
  const time = { now: 0, pending: [] };
  time.setTimeout = (fn, ms) => {
    const entry = { fn, at: time.now + ms };
    time.pending.push(entry);
    return entry;
  };
  time.clearTimeout = entry => {
    time.pending = time.pending.filter(e => e !== entry);
  };
  time.advance = ms => {
    time.now += ms;
    for (const entry of time.pending.filter(e => e.at <= time.now)) {
      time.pending = time.pending.filter(e => e !== entry);
      entry.fn();
    }
  };
  return time;
}

function make(time) {
  const calls = [];
  const taps = new TapOrDoubleTap(() => calls.push('single'), () => calls.push('double'), time, 300);
  return { taps, calls };
}

test('one tap runs the single-tap action once the double-tap time has passed', () => {
  const time = fakeTime();
  const { taps, calls } = make(time);
  taps.tap();
  time.advance(299);
  assert.deepEqual(calls, []);
  time.advance(1);
  assert.deepEqual(calls, ['single']);
});

test('a second tap within the double-tap time runs the double-tap action instead', () => {
  const time = fakeTime();
  const { taps, calls } = make(time);
  taps.tap();
  time.advance(150);
  taps.tap();
  time.advance(1000);
  assert.deepEqual(calls, ['double']);
});

test('a second tap that is too late is a new single tap after the first one ran', () => {
  const time = fakeTime();
  const { taps, calls } = make(time);
  taps.tap();
  time.advance(400);
  taps.tap();
  time.advance(400);
  assert.deepEqual(calls, ['single', 'single']);
});

test('taps for 0.3 seconds after a double tap are ignored, so a triple tap does not open the single action', () => {
  const time = fakeTime();
  const { taps, calls } = make(time);
  taps.tap();
  time.advance(100);
  taps.tap();
  time.advance(100);
  taps.tap();
  time.advance(1000);
  assert.deepEqual(calls, ['double']);
});

test('after the cooldown a tap works again', () => {
  const time = fakeTime();
  const { taps, calls } = make(time);
  taps.tap();
  time.advance(100);
  taps.tap();
  time.advance(COOLDOWN_MS + 1);
  taps.tap();
  time.advance(400);
  assert.deepEqual(calls, ['double', 'single']);
});

test('the cooldown is 300 milliseconds, like the Android app', () => {
  assert.equal(COOLDOWN_MS, 300);
});
