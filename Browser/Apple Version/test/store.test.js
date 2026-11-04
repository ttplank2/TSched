import { test } from 'node:test';
import assert from 'node:assert/strict';
import { EventStore } from '../js/store.js';
import { fixed } from '../js/event.js';

function fakeStorage() {
  const data = new Map();
  return {
    getItem: key => (data.has(key) ? data.get(key) : null),
    setItem: (key, value) => data.set(key, String(value)),
  };
}

test('a new store has no events', () => {
  assert.deepEqual(new EventStore(fakeStorage()).load(), []);
});

test('saved events come back after the store is made again, like after reopening the page', () => {
  const storage = fakeStorage();
  const e = fixed({ name: 'Math', day: 'MONDAY', startMinute: 540, endMinute: 590 });
  new EventStore(storage).save([e]);
  assert.deepEqual(new EventStore(storage).load(), [e]);
});

test('put replaces the event with the same id, or adds a new one', () => {
  const store = new EventStore(fakeStorage());
  const a = fixed({ name: 'A', day: 'MONDAY', startMinute: 60, endMinute: 120 });
  const b = fixed({ name: 'B', day: 'MONDAY', startMinute: 180, endMinute: 240 });
  store.put(a);
  store.put(b);
  store.put({ ...a, name: 'A2' });
  assert.deepEqual(store.load().map(e => e.name).sort(), ['A2', 'B']);
});

test('saving tells the caller when the browser refuses', () => {
  const full = { getItem: () => null, setItem: () => { throw new Error('quota'); } };
  assert.equal(new EventStore(full).save([]), false);
  assert.equal(new EventStore(fakeStorage()).save([]), true);
});
