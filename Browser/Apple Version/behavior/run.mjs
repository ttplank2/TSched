// Behavior tests: opens the real app in a real (headless) Chrome set up like a phone and uses it the way a person would,
// with taps and swipes, checking what the page then shows. Nothing to install: it needs Chrome (or Edge) on the computer.
// Run it with `npm run behavior` (set CHROME_PATH if Chrome is somewhere unusual).
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import http from 'node:http';
import { tmpdir } from 'node:os';
import { dirname, extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

// ---- a small web server for the app's files
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.ttf': 'font/ttf' };
const server = http.createServer((request, response) => {
  const path = join(root, new URL(request.url, 'http://x').pathname.replace(/^\/$/, '/index.html'));
  if (!path.startsWith(root) || !existsSync(path)) {
    response.writeHead(404).end();
    return;
  }
  response.writeHead(200, { 'Content-Type': TYPES[extname(path)] ?? 'application/octet-stream', 'Cache-Control': 'no-store' });
  response.end(readFileSync(path));
});
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const site = `http://127.0.0.1:${server.address().port}/index.html`;

// ---- Chrome, driven through its DevTools port
const CHROMES = [
  process.env.CHROME_PATH,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
].filter(Boolean);
const chromePath = CHROMES.find(existsSync);
if (!chromePath) {
  console.error('Chrome or Edge was not found. Set CHROME_PATH to its program.');
  process.exit(2);
}
const profile = mkdtempSync(join(tmpdir(), 'tsched-behavior-'));
const debugPort = 9300 + Math.floor(Math.random() * 500);
const chrome = spawn(chromePath, ['--headless=new', `--remote-debugging-port=${debugPort}`, `--user-data-dir=${profile}`,
  '--no-first-run', '--no-default-browser-check', '--no-sandbox', 'about:blank'], { stdio: 'ignore' });

async function connect() {
  for (let tries = 0; tries < 100; tries++) {
    try {
      const pages = await (await fetch(`http://127.0.0.1:${debugPort}/json`)).json();
      const page = pages.find(p => p.type === 'page');
      if (page) {
        return page.webSocketDebuggerUrl;
      }
    } catch {
      // Chrome is still starting.
    }
    await sleep(100);
  }
  throw new Error('Chrome did not start');
}

const socket = new WebSocket(await connect());
await new Promise(resolve => { socket.onopen = resolve; });
let nextId = 0;
const waiting = new Map();
socket.onmessage = message => {
  const data = JSON.parse(message.data);
  if (waiting.has(data.id)) {
    waiting.get(data.id)(data);
    waiting.delete(data.id);
  }
};
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++nextId;
  waiting.set(id, data => (data.error ? reject(new Error(`${method}: ${data.error.message}`)) : resolve(data.result)));
  socket.send(JSON.stringify({ id, method, params }));
});

// A phone-sized screen that is touched, not clicked.
await send('Page.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 412, height: 892, deviceScaleFactor: 2, mobile: true });
await send('Emulation.setTouchEmulationEnabled', { enabled: true });

/** Runs JavaScript in the page and gives back its result. */
async function ev(expression) {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) {
    throw new Error(`In the page: ${result.exceptionDetails.exception?.description ?? result.exceptionDetails.text}`);
  }
  return result.result.value;
}

async function waitFor(expression, what, ms = 4000) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    if (await ev(expression)) {
      return;
    }
    await sleep(50);
  }
  throw new Error(`Timed out waiting for ${what}`);
}

// ---- doing things the way a person does
const touch = (type, points) => send('Input.dispatchTouchEvent', { type, touchPoints: points.map(([x, y]) => ({ x, y })) });

async function tapAt(x, y) {
  await touch('touchStart', [[x, y]]);
  await sleep(40);
  await touch('touchEnd', []);
  await sleep(60);
}

/** The middle of the first visible element matching the selector (with the text, if given), scrolled into view. */
async function centerOf(selector, text = null) {
  const spot = await ev(`(() => {
    const nodes = [...document.querySelectorAll(${JSON.stringify(selector)})]
      .filter(n => ${JSON.stringify(text)} === null || n.textContent.trim() === ${JSON.stringify(text)});
    const node = nodes.find(n => { const r = n.getBoundingClientRect(); return r.width > 0 && r.height > 0; });
    if (!node) return null;
    node.scrollIntoView({ block: 'center', inline: 'center' });
    const r = node.getBoundingClientRect();
    return [r.left + r.width / 2, r.top + r.height / 2];
  })()`);
  assert.ok(spot, `found ${selector}${text ? ` "${text}"` : ''} on the screen`);
  return spot;
}

async function tap(selector, text = null) {
  const [x, y] = await centerOf(selector, text);
  await tapAt(x, y);
}

/** A finger drag from one place to another, over about `over` milliseconds. */
async function swipe([x1, y1], [x2, y2], over = 250, steps = 10) {
  await touch('touchStart', [[x1, y1]]);
  for (let i = 1; i <= steps; i++) {
    await sleep(over / steps);
    await touch('touchMove', [[x1 + ((x2 - x1) * i) / steps, y1 + ((y2 - y1) * i) / steps]]);
  }
  await touch('touchEnd', []);
}

/** Opens the app with nothing saved (or with the events given as a function run in the page), phone sized. */
async function openApp({ setup = null, skipTour = true } = {}) {
  await send('Page.navigate', { url: 'about:blank' });
  await send('Page.navigate', { url: site });
  await waitFor(`document.readyState === 'complete' && document.querySelector('#screen .day')`, 'the calendar');
  await ev('localStorage.clear()');
  if (setup) {
    await ev(`(async () => { ${setup} })()`);
  }
  await send('Page.reload');
  await waitFor(`document.readyState === 'complete' && document.querySelector('#screen .day')`, 'the calendar after reloading');
  await sleep(300);
  if (skipTour) {
    const touring = await ev(`!!document.querySelector('.tour-cover')`);
    if (touring) {
      await tap('.tour-card .buttons .btn', 'Skip');
    }
  }
}

const shownDay = () => ev(`[...document.querySelectorAll('.day')].find(d => d.style.visibility !== 'hidden' && Math.abs(parseFloat(d.style.transform.replace('translateX(', ''))) < 1)?.dataset.day`);
const scrollTopOfShownDay = () => ev(`[...document.querySelectorAll('.day')].find(d => d.style.transform === 'translateX(0px)').querySelector('.scroller').scrollTop`);
const savedEvents = () => ev(`(async () => { const { decode } = await import('/js/codec.js'); return decode(localStorage.getItem('tsched.events') ?? '').map(e => ({ name: e.name, day: e.day, kind: e.kind, start: e.startMinute })); })()`);
const addEventsInPage = lines => `
  const { fixed, free } = await import('/js/event.js');
  const { encode } = await import('/js/codec.js');
  localStorage.setItem('tsched.events', encode([${lines}]));`;

/** Presses the Start (0) or End (1) button on Add Event and types a time into the clock. */
async function pickTime(buttonIndex, hour, minute) {
  const [x, y] = await ev(`(() => { const b = document.querySelectorAll('.btn.choice')[${buttonIndex}]; b.scrollIntoView({ block: 'center' }); const r = b.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; })()`);
  await tapAt(x, y);
  await waitFor(`document.querySelector('.clock-dialog')`, 'the clock');
  await tap('.tkeyboard');
  await ev(`(() => { const [h, m] = document.querySelectorAll('.tfield'); h.value = '${hour}'; m.value = '${minute}'; })()`);
  await tap('.dialog .buttons .btn', 'OK');
  await waitFor(`!document.querySelector('.clock-dialog')`, 'the clock to close');
}

// ---- the scenarios
const scenarios = [];
const scenario = (name, run) => scenarios.push({ name, run });

scenario('the first time the app opens with no events, the welcome tutorial appears, and Skip closes it', async () => {
  await openApp({ skipTour: false });
  await waitFor(`document.querySelector('.tour-card h3')?.textContent === 'Welcome to TSched'`, 'the welcome step');
  await tap('.tour-card .buttons .btn', 'Skip');
  assert.equal(await ev(`!!document.querySelector('.tour-cover')`), false);
});

scenario('a sideways swipe moves to the next day, seven in a row come back round to the same day', async () => {
  await openApp();
  const start = await shownDay();
  const week = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
  await swipe([300, 400], [60, 405]);
  await sleep(500);
  assert.equal(await shownDay(), week[(week.indexOf(start) + 1) % 7], 'swiping left shows the next day');
  for (let i = 0; i < 6; i++) {
    await swipe([300, 400], [60, 405]);
    await sleep(500);
  }
  assert.equal(await shownDay(), start, 'seven swipes go all the way round the week');
  await swipe([60, 400], [320, 395]);
  await sleep(500);
  assert.notEqual(await shownDay(), start, 'a swipe to the right goes back a day');
});

scenario('the 45 degree rule: a mostly up-and-down swipe scrolls the day, and a mostly sideways one changes it', async () => {
  await openApp();
  const start = await shownDay();
  const before = await scrollTopOfShownDay();
  await swipe([200, 600], [240, 300], 600);
  await sleep(300);
  assert.equal(await shownDay(), start, 'a steep swipe does not change the day');
  assert.notEqual(await scrollTopOfShownDay(), before, 'a steep swipe scrolls the day');
  await swipe([300, 500], [60, 540], 300);
  await sleep(500);
  assert.notEqual(await shownDay(), start, 'a shallow swipe changes the day');
});

scenario('a fast flick up or down keeps scrolling after the finger lifts, and a press stops it at once', async () => {
  await openApp();
  // Start partway down, so there is room to scroll further in either direction.
  await ev(`[...document.querySelectorAll('.day')].find(d => d.style.transform === 'translateX(0px)').querySelector('.scroller').scrollTop = 300`);
  await swipe([200, 600], [200, 450], 80, 8);
  const lifted = await scrollTopOfShownDay();
  await sleep(150);
  const later = await scrollTopOfShownDay();
  assert.ok(later > lifted, `the day carried on scrolling (${lifted} then ${later})`);
  await tapAt(200, 500);
  const pressed = await scrollTopOfShownDay();
  await sleep(300);
  assert.equal(await scrollTopOfShownDay(), pressed, 'it stayed still after the press');
});

scenario('adding a fixed event shows it on the calendar and it is still there after reloading', async () => {
  await openApp();
  await tap('.add');
  await waitFor(`document.querySelector('input[aria-label="Name"]')`, 'the Add Event screen');
  await ev(`(() => { const n = document.querySelector('input[aria-label="Name"]'); n.value = 'Physics Lab'; n.dispatchEvent(new Event('input', { bubbles: true })); })()`);
  await tap('.choice-row .btn', 'Mon');
  await pickTime(0, '10', '00');
  await pickTime(1, '11', '30');
  await tap('.save');
  await waitFor(`document.querySelector('.day')`, 'the calendar');
  const names = await ev(`[...document.querySelectorAll('.day[data-day="MONDAY"] .block .name')].map(n => n.textContent)`);
  assert.deepEqual(names, ['Physics Lab'], 'the event is on Monday');
  await send('Page.reload');
  await waitFor(`document.querySelector('.day[data-day="MONDAY"] .block .name')`, 'the event after reloading');
  assert.deepEqual((await savedEvents()).map(e => e.name), ['Physics Lab']);
});

scenario('tapping an event opens its popup; Edit changes it; Delete asks first and then removes it', async () => {
  await openApp({ setup: addEventsInPage(`fixed({ name: 'Gym', day: 'TUESDAY', startMinute: 600, durationMinutes: 60 })`) });
  await ev(`document.querySelector('.day[data-day="TUESDAY"] .block').click()`);
  await waitFor(`document.querySelector('.dialog h3')?.textContent === 'Gym'`, 'the event popup');
  await tap('.dialog .buttons .btn', 'Edit');
  await waitFor(`document.querySelector('.title')?.textContent === 'Edit Event'`, 'the Edit Event screen');
  assert.equal(await ev(`document.querySelector('input[aria-label="Name"]').value`), 'Gym', 'the form is filled in');
  await ev(`(() => { document.querySelector('input[aria-label="Name"]').value = 'Gym 2'; })()`);
  await tap('.save');
  await waitFor(`document.querySelector('.day')`, 'the calendar');
  assert.deepEqual((await savedEvents()).map(e => e.name), ['Gym 2'], 'saved once, renamed');
  await ev(`document.querySelector('.day[data-day="TUESDAY"] .block').click()`);
  await waitFor(`document.querySelector('.dialog h3')`, 'the popup again');
  await tap('.dialog .buttons .btn', 'Delete');
  await waitFor(`document.querySelector('.dialog h3')?.textContent.startsWith('Delete')`, 'the question before deleting');
  assert.equal((await savedEvents()).length, 1, 'nothing is deleted before the answer');
  await tap('.dialog .buttons .btn', 'Delete');
  await waitFor(`!document.querySelector('.dialog')`, 'the popup to close');
  assert.equal((await savedEvents()).length, 0, 'deleted');
});

scenario('a name too long for its block is cut with "..." and stays inside the block', async () => {
  await openApp({ setup: addEventsInPage(`fixed({ name: 'A'.repeat(60), day: 'WEDNESDAY', startMinute: 600, durationMinutes: 120 })`) });
  const result = await ev(`(() => { const n = document.querySelector('.day[data-day="WEDNESDAY"] .block .name'); return { text: n.textContent, fits: n.scrollWidth <= n.clientWidth }; })()`);
  assert.ok(result.text.endsWith('...'), `ends with an ellipsis: ${result.text}`);
  assert.ok(result.fits, 'the text does not run past the block');
});

scenario('a free event can be placed: Place Events suggests a time, and Approve puts it on the calendar', async () => {
  await openApp({ setup: addEventsInPage(`free({ name: 'Study', durationMinutes: 90 })`) });
  await ev(`location.hash = '#/free'`);
  await waitFor(`document.querySelector('.list-row')`, 'the free events list');
  await tap('.btn', 'Place Events');
  await waitFor(`document.querySelector('.title')?.textContent === 'Place Events'`, 'the Place Events screen');
  await tap('.save', 'Place');
  await waitFor(`document.querySelector('.dialog h3')?.textContent === 'Suggested Placement'`, 'the suggestion');
  assert.equal((await savedEvents())[0].day, null, 'nothing moves before it is approved');
  await tap('.dialog .buttons .btn', 'Approve');
  await waitFor(`!document.querySelector('.dialog')`, 'the popup to close');
  const [study] = await savedEvents();
  assert.ok(study.day, `Study is now on ${study.day}`);
});

scenario('the settings the web version cannot change say so instead of changing', async () => {
  await openApp();
  await ev(`location.hash = '#/settings'`);
  await waitFor(`document.querySelector('.stack')`, 'Settings');
  await tap('.btn.setting', 'Lock Vertical Calendar');
  await waitFor(`document.querySelector('#toast')?.textContent.includes('isn’t in the web version yet')`, 'the message');
  assert.equal(await ev(`document.querySelector('.btn.setting.toggle').classList.contains('on')`), true, 'Lock Vertical Calendar is still on');
});

// ---- run them
let failed = 0;
for (const { name, run } of scenarios) {
  if (process.env.ONLY && !name.includes(process.env.ONLY)) {
    continue;
  }
  try {
    await run();
    console.log(`PASS  ${name}`);
  } catch (problem) {
    failed++;
    console.log(`FAIL  ${name}\n      ${problem.message.split('\n')[0]}`);
  }
}
console.log(failed === 0 ? `\nAll ${scenarios.length} behavior tests passed.` : `\n${failed} of ${scenarios.length} behavior tests failed.`);

await send('Browser.close').catch(() => {});
chrome.kill();
server.close();
await sleep(300);
rmSync(profile, { recursive: true, force: true });
process.exit(failed === 0 ? 0 : 1);
