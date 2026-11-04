import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadSettings, saveSettings, DEFAULT_SETTINGS } from '../js/settings.js';
import { DEFAULT, COLORS } from '../js/colors.js';

const storage = text => ({ getItem: () => text });

test('nothing saved gives the defaults', () => {
  assert.deepEqual(loadSettings(storage(null)), DEFAULT_SETTINGS);
  assert.equal(DEFAULT_SETTINGS.accent, DEFAULT);
});

test('unreadable settings give the defaults', () => {
  assert.deepEqual(loadSettings(storage('{not json')), DEFAULT_SETTINGS);
});

test('saved choices are kept and the rest come from the defaults', () => {
  const loaded = loadSettings(storage(JSON.stringify({ accent: COLORS[0], startMinute: 480 })));
  assert.equal(loaded.accent, COLORS[0]);
  assert.equal(loaded.startMinute, 480);
  assert.equal(loaded.todayAtNow, DEFAULT_SETTINGS.todayAtNow);
});

test('an accent that is not on the wheel becomes yellow', () => {
  assert.equal(loadSettings(storage('{"accent":123}')).accent, DEFAULT);
  assert.equal(loadSettings(storage('{"accent":"red"}')).accent, DEFAULT);
});

test('an old darker shade becomes the current one', () => {
  assert.equal(loadSettings(storage(JSON.stringify({ accent: 0xFFA1887F }))).accent, DEFAULT);
});

test('the locked settings keep their values even if an older save says otherwise', () => {
  const loaded = loadSettings(storage('{"lockVertical":false,"vibration":true,"sound":true}'));
  assert.equal(loaded.lockVertical, true);
  assert.equal(loaded.vibration, false);
  assert.equal(loaded.sound, false);
});

test('saving writes the settings, and says so when the browser refuses', () => {
  const written = {};
  assert.equal(saveSettings({ setItem: (key, value) => { written[key] = value; } }, DEFAULT_SETTINGS), true);
  assert.deepEqual(JSON.parse(written['tsched.settings']), DEFAULT_SETTINGS);
  assert.equal(saveSettings({ setItem: () => { throw new Error('full'); } }, DEFAULT_SETTINGS), false);
});
