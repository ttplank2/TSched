// Regression tests: each one guards a bug that was found and fixed, so it cannot quietly come back.
// Some of these bugs only show in a real browser, so the test reads the file the fix lives in and checks the fix is there.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { swipeIntent } from '../js/pager.js';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = file => readFileSync(join(root, file), 'utf8');
const css = read('css/style.css');

const count = (text, piece) => text.split(piece).length - 1;

test('2026-11-03: a drag that begins as a scroll still becomes a swipe once it is more sideways than vertical', () => {
  // The calendar checks this all through a drag; the rule it asks is this one.
  assert.equal(swipeIntent(30, 31, 8), 'vertical');
  assert.equal(swipeIntent(32, 31, 8), 'sideways');
});

test('2026-11-04: touches inside a scrolling day are left to the calendar, or phones cancel the swipe', () => {
  assert.match(css, /\.pager \*,\s*\.pager \{\s*touch-action: none;/);
});

test('2026-11-04: double-tap zoom is off everywhere, and pinch zoom is stopped by the page', () => {
  assert.match(css, /\*\s*\{[^}]*touch-action: manipulation;/);
  const html = read('index.html');
  assert.match(html, /maximum-scale=1/);
  assert.match(html, /user-scalable=no/);
  const app = read('js/app.js');
  assert.match(app, /gesturestart/);
  assert.match(app, /touches\.length > 1/);
});

test('2026-11-10: the iPhone Home Screen icon is a PNG that exists (iPhones do not accept an SVG)', () => {
  const link = read('index.html').match(/rel="apple-touch-icon" href="([^"]+)"/);
  assert.ok(link, 'the page names an apple-touch-icon');
  assert.match(link[1], /\.png$/);
  assert.ok(existsSync(join(root, link[1])), 'the icon file is there');
});

test('2026-11-04: no style is defined twice (an old copy of the slider and number picker styles once overrode the new ones)', () => {
  for (const selector of ['.sl {', '.sl-thumb {', '.np {', '.np-row {', '.length {']) {
    assert.equal(count(css, `\n${selector}`), 1, `${selector} is defined once`);
  }
});

test('2026-11-10: block text is measured on the page itself, so names fit on phones with their own text sizes', () => {
  assert.match(read('js/calendar.js'), /getBoundingClientRect\(\)\.width/);
});

test('2026-11-10: picking a color swallows the click that follows, so it cannot land on the page behind the popup', () => {
  assert.match(read('js/wheel.js'), /addEventListener\('click', swallow, true\)/);
});
