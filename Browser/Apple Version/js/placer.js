/**
 * Suggests where to put unplaced free events: in open time between the week's fixed and already-placed events,
 * inside the hours the person allows, on days each event allows. A port of the Android app's FreeEventPlacer.
 *
 * Events with fewer allowed days and longer lengths go first, since they are hardest to fit. Each goes on the
 * allowed day with the most open time left, so the week stays balanced.
 */
import { DAYS, MINUTES_PER_DAY, allowsDay, placedAt, unplaced, isPlaced, endMinute, crossesMidnight } from './event.js';

/** A shuffled copy of a list, using `random()` (0 up to 1) so a seed makes the order repeatable. */
function shuffled(list, random) {
  const copy = [...list];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

/** The open gaps of a day within the window, as [start, end) pairs. */
function gaps(busy, windowStart, windowEnd) {
  const sorted = [...busy].sort((a, b) => a[0] - b[0]);
  const found = [];
  let cursor = windowStart;
  for (const [start, end] of sorted) {
    if (start > cursor) {
      found.push([cursor, Math.min(start, windowEnd)]);
    }
    cursor = Math.max(cursor, end);
    if (cursor >= windowEnd) {
      break;
    }
  }
  if (cursor < windowEnd) {
    found.push([cursor, windowEnd]);
  }
  return found;
}

const openMinutes = (busy, windowStart, windowEnd) =>
  gaps(busy, windowStart, windowEnd).reduce((total, [start, end]) => total + end - start, 0);

/** Start times that fit: the beginning of each gap long enough, which keeps the rest of the gap in one piece. */
const fittingStarts = (busy, windowStart, windowEnd, length) =>
  gaps(busy, windowStart, windowEnd).filter(([start, end]) => end - start >= length).map(([start]) => start);

const allowedDayCount = event => DAYS.filter(day => allowsDay(event, day)).length;

/**
 * @param week every saved event; fixed and placed ones are treated as busy time
 * @param windowStart earliest minute of the day an event may start
 * @param windowEnd minute of the day by which events must end
 * @param random returns 0 up to 1; breaks ties so "Try another" can give a different layout
 * @returns {{ placed: object[], unfitted: object[] }}
 */
export function suggest(week, windowStart, windowEnd, random = Math.random) {
  if (windowStart < 0 || windowEnd > MINUTES_PER_DAY || windowStart >= windowEnd) {
    throw new Error('The end time must be after the start time.');
  }
  const busy = Object.fromEntries(DAYS.map(day => [day, []]));
  const toPlace = [];
  for (const event of week) {
    if (event.day != null) {
      busy[event.day].push([event.startMinute, Math.min(endMinute(event), MINUTES_PER_DAY)]);
      if (crossesMidnight(event)) {
        // An overnight event also keeps the start of the next day busy.
        busy[DAYS[(DAYS.indexOf(event.day) + 1) % 7]].push([0, endMinute(event) - MINUTES_PER_DAY]);
      }
    } else {
      toPlace.push(event);
    }
  }
  const ordered = shuffled(toPlace, random).sort((a, b) =>
    allowedDayCount(a) - allowedDayCount(b) || b.durationMinutes - a.durationMinutes);

  const result = { placed: [], unfitted: [] };
  for (const event of ordered) {
    const days = shuffled(DAYS.filter(day => allowsDay(event, day)), random)
      .sort((a, b) => openMinutes(busy[b], windowStart, windowEnd) - openMinutes(busy[a], windowStart, windowEnd));
    let placed = null;
    for (const day of days) {
      const starts = fittingStarts(busy[day], windowStart, windowEnd, event.durationMinutes);
      if (starts.length > 0) {
        const start = starts[Math.floor(random() * starts.length)];
        placed = placedAt(event, day, start);
        busy[day].push([start, start + event.durationMinutes]);
        break;
      }
    }
    (placed ? result.placed : result.unfitted).push(placed ?? event);
  }
  return result;
}

/** The week with every placed free event taken off the calendar; fixed events are unchanged. */
export const recallAll = week => week.map(event => (isPlaced(event) ? unplaced(event) : event));

/** How many free events in the week are placed on the calendar. */
export const countPlaced = week => week.filter(isPlaced).length;

/** A random number generator that gives the same numbers for the same seed, for repeatable tests. */
export function seeded(seed) {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
