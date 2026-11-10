// Break-it check: copies the app, breaks one rule at a time on purpose, and reports whether the tests notice.
// A break the tests do NOT catch (MISSED) shows a rule nothing is guarding. Run it with `npm run break-it`.
import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

/** [what is broken, file, text to find, text to put in its place] */
const BREAKS = [
  ['The 45 degree rule counts a perfect diagonal as sideways', 'js/pager.js', 'sideways > vertical', 'sideways >= vertical'],
  ['Events under 30 minutes get text', 'js/blocktext.js', 'MIN_MINUTES_FOR_TEXT = 30', 'MIN_MINUTES_FOR_TEXT = 10'],
  ['Placed free events no longer block each other', 'js/placer.js', '        busy[day].push([start, start + event.durationMinutes]);\n', ''],
  ['Editing a counted event forgets what each day has left', 'js/groups.js', 'const kept = keepCounts ?', 'const kept = false ?'],
  ['Vibration is no longer locked off', 'js/settings.js', 'lockVertical: true, vibration: false, sound: false });', 'lockVertical: true, vibration: true, sound: false });'],
  ['An overnight event stops showing on the next morning', 'js/layout.js', 'continuesFrom', 'continuesFromX'],
  ['Dragging a day coasts forever (speed never drops)', 'js/dragscroll.js', 'velocity *= Math.exp(-3 * dt);', ''],
  ['Touches inside a scrolling day go back to the browser (swipes are cancelled on phones)', 'css/style.css', '.pager *,', '.pager-x *,'],
  ['The iPhone Home Screen icon points at the SVG again', 'index.html', 'href="icon-180.png"', 'href="icon.svg"'],
  ['Picking a color no longer swallows the click that follows', 'js/wheel.js', "document.addEventListener('click', swallow, true);", ''],
  ['Pinch zoom is no longer stopped', 'js/app.js', 'event.touches.length > 1', 'event.touches.length > 99'],
  ['A stylesheet rule the slider needs is renamed', 'css/style.css', '.sl-thumb {', '.sl-thumb-x {'],
];

/** Breaks only the behavior tests can see (they use the app on screen). Run with `npm run break-it -- --behavior`. */
const BEHAVIOR_BREAKS = [
  ['Swiping left shows the day before instead of the next day', 'js/calendar.js', 'show(gesture.basePosition - (event.clientX - gesture.baseX) / PAGE_STRIDE);', 'show(gesture.basePosition + (event.clientX - gesture.baseX) / PAGE_STRIDE);'],
  ['A fast flick no longer coasts', 'js/calendar.js', 'coastScroll(finished.scroller, Math.max(-4000, Math.min(4000, speed)));', ''],
  ['Save Event does not save', 'js/forms.js', "ctx.saveAll(saved, `Saved “${name.value.trim() || 'Free time'}”`, '/');", "ctx.go('/');"],
  ['Delete removes the event without asking first', 'js/calendar.js', 'onclick: () => confirmDelete(event, group, events, ctx) },', "onclick: () => ctx.saveAll(events.filter(saved => saved.id !== event.id), 'Event deleted') },"],
  ['Long names run past the edge of their block', 'js/calendar.js', 'return measureSpan.getBoundingClientRect().width;', 'return 0;'],
  ['Approve does not place the events', 'js/place.js', "ctx.saveAll(ctx.events.map(event => byId.get(event.id) ?? event), `Placed ${count} ${count === 1 ? 'event' : 'events'} on your calendar`, '/free');", "ctx.go('/free');"],
];
const behavior = process.argv.includes('--behavior');
const list = behavior ? BEHAVIOR_BREAKS : BREAKS;
const command = behavior ? ['behavior/run.mjs'] : ['--test', 'test/*.test.js'];

const work = mkdtempSync(join(tmpdir(), 'tsched-break-'));
let missed = 0;
for (const [what, file, find, replacement] of list) {
  rmSync(work, { recursive: true, force: true });
  cpSync(root, work, { recursive: true, filter: source => !source.includes('node_modules') });
  const target = join(work, file);
  const text = readFileSync(target, 'utf8');
  if (!text.includes(find)) {
    console.log(`SKIPPED (text not found, update this list): ${what}`);
    continue;
  }
  writeFileSync(target, text.replace(find, replacement));
  const result = spawnSync(process.execPath, command, { cwd: work, encoding: 'utf8' });
  const caught = result.status !== 0;
  missed += caught ? 0 : 1;
  console.log(`${caught ? 'CAUGHT' : 'MISSED'}  ${what}`);
}
rmSync(work, { recursive: true, force: true });
console.log(missed === 0 ? '\nEvery break was caught.' : `\n${missed} break(s) were not caught: nothing is guarding those rules yet.`);
