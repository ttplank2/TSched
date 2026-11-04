/**
 * Turns events into text for saving and back, in the same format the Android app uses: each event is one
 * line of twelve tab-separated fields, with tabs, newlines and backslashes inside names escaped.
 */
import { DAYS, NO_GROUP, fromSaved } from './event.js';
import { DEFAULT, onWheel } from './colors.js';

const NO_DAY = '-';
const FIELD_COUNT = 12;

function escape(text) {
  return text.replaceAll('\\', '\\\\').replaceAll('\t', '\\t').replaceAll('\n', '\\n');
}

function unescape(text) {
  let result = '';
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (c === '\\' && i + 1 < text.length) {
      const next = text[++i];
      result += next === 't' ? '\t' : next === 'n' ? '\n' : next;
    } else {
      result += c;
    }
  }
  return result;
}

export function encode(events) {
  return events.map(event => [
    escape(event.id),
    event.kind,
    escape(event.name),
    escape(event.location),
    event.day ?? NO_DAY,
    event.startMinute,
    event.durationMinutes,
    event.allowedDays.length === 0 ? NO_DAY : event.allowedDays.join(','),
    NO_DAY,
    (event.color >>> 0).toString(16),
    escape(event.groupId),
    event.count,
  ].join('\t') + '\n').join('');
}

function parseDay(field) {
  return DAYS.includes(field) ? field : null;
}

function parseAllowedDays(first, second) {
  const start = parseDay(first);
  const last = parseDay(second);
  if (start && last) {
    // Older files hold the first and last day of a range, which may wrap past Sunday.
    const days = [];
    for (let i = DAYS.indexOf(start); ; i = (i + 1) % 7) {
      days.push(DAYS[i]);
      if (DAYS[i] === last) {
        break;
      }
    }
    return days;
  }
  return first.split(',').filter(name => DAYS.includes(name));
}

function decodeFields(fields) {
  const color = onWheel(parseInt(fields[9], 16) || DEFAULT);
  const kind = fields[1];
  if (!['FIXED', 'FREE', 'COUNTED'].includes(kind)) {
    throw new Error('Unknown kind');
  }
  const startMinute = Number.parseInt(fields[5], 10);
  const durationMinutes = Number.parseInt(fields[6], 10);
  if (Number.isNaN(startMinute) || Number.isNaN(durationMinutes)) {
    throw new Error('Bad number');
  }
  const base = {
    id: unescape(fields[0]),
    kind,
    name: unescape(fields[2]),
    location: unescape(fields[3]),
    startMinute,
    durationMinutes,
    color,
    groupId: unescape(fields[10]) || NO_GROUP,
  };
  if (kind === 'FREE') {
    return fromSaved({ ...base, day: parseDay(fields[4]), allowedDays: parseAllowedDays(fields[7], fields[8]) });
  }
  const day = parseDay(fields[4]);
  if (!day) {
    throw new Error('Pick a day');
  }
  return fromSaved({ ...base, day, count: kind === 'COUNTED' ? Number.parseInt(fields[11], 10) : 0 });
}

/** Reads events back, skipping any line that is damaged or no longer valid. */
export function decode(text) {
  const events = [];
  for (const line of text.split('\n')) {
    const fields = line.split('\t');
    if (fields.length !== FIELD_COUNT) {
      continue;
    }
    try {
      events.push(decodeFields(fields));
    } catch {
      // A corrupt line should not cost the person every other event.
    }
  }
  return events;
}
