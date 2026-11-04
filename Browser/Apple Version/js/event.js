/**
 * One event in the week, as a plain object. A FIXED event has a day and start time and never moves.
 * A FREE event has only a length (and optionally the days it may happen on); once the user approves a
 * placement it also has a day and start time. A COUNTED event is a fixed event that runs out. Times are
 * minutes after midnight; days are 'SUNDAY' to 'SATURDAY'. The rules and messages match the Android app.
 */
import { DEFAULT } from './colors.js';

export const MINUTES_PER_DAY = 24 * 60;
export const NO_TIME = -1;
export const MIN_DURATION_MINUTES = 15;
export const NO_GROUP = '';
export const MAX_COUNT = 52;

export const DAYS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

export function newId() {
  return globalThis.crypto.randomUUID();
}

function trim(text) {
  return text == null ? '' : String(text).trim();
}

function requireName(name, message) {
  const trimmed = trim(name);
  if (!trimmed) {
    throw new Error(message);
  }
  return trimmed;
}

/** Rejects a length under the minimum. Only used when a person makes or edits an event. */
export function requireMinimumLength(durationMinutes) {
  if (durationMinutes > 0 && durationMinutes < MIN_DURATION_MINUTES) {
    throw new Error(`Events must be at least ${MIN_DURATION_MINUTES} minutes long.`);
  }
}

/** No days or every day both mean "any day", stored as an empty list; otherwise in week order. */
function normalizeDays(days) {
  if (!days || days.length === 0 || new Set(days).size === DAYS.length) {
    return [];
  }
  return DAYS.filter(day => days.includes(day));
}

function make(fields) {
  return Object.freeze({
    id: fields.id ?? newId(),
    kind: fields.kind,
    name: fields.name,
    location: fields.location ?? '',
    day: fields.day ?? null,
    startMinute: fields.startMinute ?? NO_TIME,
    durationMinutes: fields.durationMinutes,
    allowedDays: fields.allowedDays ?? [],
    color: fields.color ?? DEFAULT,
    groupId: fields.groupId ?? NO_GROUP,
    count: fields.count ?? 0,
  });
}

function timed(fields, kindMessage) {
  const name = requireName(fields.name, kindMessage);
  if (!fields.day) {
    throw new Error('Pick a day for the event.');
  }
  const start = fields.startMinute;
  if (start == null || start < 0 || start >= MINUTES_PER_DAY) {
    throw new Error('Pick a start time.');
  }
  const duration = fields.durationMinutes ?? (fields.endMinute == null ? 0 : fields.endMinute - start);
  if (duration <= 0) {
    throw new Error('The end time must be after the start time.');
  }
  if (duration > MINUTES_PER_DAY) {
    throw new Error('An event can be at most 24 hours long.');
  }
  return { name, duration, start };
}

/** Creates a fixed event: needs a name, day, start, and either an end or a duration. */
export function fixed(fields) {
  const { name, duration, start } = timed(fields, 'A fixed event needs a name.');
  if (fields.endMinute != null && fields.durationMinutes == null) {
    requireMinimumLength(duration);
  }
  return make({ ...fields, kind: 'FIXED', name, location: trim(fields.location), startMinute: start, durationMinutes: duration, allowedDays: [] });
}

/** Creates a counted event: a fixed event that happens `count` more times. */
export function counted(fields) {
  const count = fields.count;
  if (!Number.isInteger(count) || count < 1 || count > MAX_COUNT) {
    throw new Error(`The count must be from 1 to ${MAX_COUNT}.`);
  }
  const base = fixed(fields);
  return make({ ...base, kind: 'COUNTED', count });
}

/** Creates a free event, which may already be placed (a day and start together, or neither). */
export function free(fields) {
  const name = requireName(fields.name, 'A free event needs a name.');
  const duration = fields.durationMinutes;
  if (!(duration > 0)) {
    throw new Error('A free event needs a length.');
  }
  requireMinimumLength(duration);
  if (duration > MINUTES_PER_DAY) {
    throw new Error('A free event can be at most 24 hours long.');
  }
  const day = fields.day ?? null;
  const start = fields.startMinute ?? NO_TIME;
  if ((day == null) !== (start === NO_TIME)) {
    throw new Error('A placed event needs both a day and a start time.');
  }
  if (day != null && (start < 0 || start + duration > MINUTES_PER_DAY)) {
    throw new Error('The event must fit inside one day.');
  }
  return make({ ...fields, kind: 'FREE', name, location: trim(fields.location), day, startMinute: start, durationMinutes: duration, allowedDays: normalizeDays(fields.allowedDays) });
}

/** Rebuilds an event from saved fields without the minimum-length rule, so old short events still load. */
export function fromSaved(fields) {
  if (fields.kind === 'FREE') {
    const name = trim(fields.name) || 'Free time';
    return free({ ...fields, name, durationMinutes: fields.durationMinutes });
  }
  if (fields.kind === 'COUNTED') {
    return counted({ ...fields, durationMinutes: fields.durationMinutes });
  }
  return fixed({ ...fields, durationMinutes: fields.durationMinutes, endMinute: null });
}

export function endMinute(event) {
  return event.startMinute + event.durationMinutes;
}

/** This free event placed on a day at a start time, keeping its id and details. */
export function placedAt(event, day, startMinute) {
  return free({ ...event, day, startMinute });
}

/** This free event taken off the calendar, keeping its id, details and color. */
export function unplaced(event) {
  return free({ ...event, day: null, startMinute: NO_TIME });
}

export function withColor(event, color) {
  return Object.freeze({ ...event, color });
}

export function withGroupId(event, groupId) {
  return Object.freeze({ ...event, groupId });
}

export function withCount(event, count) {
  return Object.freeze({ ...event, count });
}

export function isPlaced(event) {
  return event.kind === 'FREE' && event.day != null;
}

export function isGrouped(event) {
  return event.groupId !== NO_GROUP;
}

/** Whether a fixed event runs past midnight into the next day, such as sleep from 11 PM to 7 AM. */
export function crossesMidnight(event) {
  return (event.kind === 'FIXED' || event.kind === 'COUNTED') && event.startMinute + event.durationMinutes > MINUTES_PER_DAY;
}

/** Whether a free event may happen on this day. */
export function allowsDay(event, day) {
  return event.allowedDays.length === 0 || event.allowedDays.includes(day);
}
