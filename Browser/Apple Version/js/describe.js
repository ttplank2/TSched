/** Plain-words descriptions of events for lists, like the Android app's EventDialogs. */
import { DAYS, endMinute, crossesMidnight } from './event.js';
import { range, length } from './time.js';
import { dayList } from './eventrows.js';

export const DAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

const dayName = day => DAY_NAMES[DAYS.indexOf(day)];

const withLocation = (text, event) => (event.location ? `${text}\nAt ${event.location}` : text);

/** When one event happens: a placed free event says where it is; an unplaced one says how long and on what days. */
export function describeEvent(event) {
  if (event.kind === 'FREE') {
    if (event.day != null) {
      return withLocation(`Placed on ${dayName(event.day)}, ${range(event.startMinute, endMinute(event))}`, event);
    }
    return describeFreeGroup([event]);
  }
  let text = `Every ${dayName(event.day)}, ${range(event.startMinute, endMinute(event))}`;
  if (crossesMidnight(event)) {
    text += ' (ends the next day)';
  }
  if (event.kind === 'COUNTED') {
    text += `\n${event.count} ${event.count === 1 ? 'time' : 'times'} left`;
  }
  return withLocation(text, event);
}

/** Unplaced free events made together on several days: the length and the days any of them allows. */
export function describeFreeGroup(members) {
  const first = members[0];
  const days = new Set();
  for (const member of members) {
    (member.allowedDays.length === 0 ? DAYS : member.allowedDays).forEach(day => days.add(day));
  }
  const text = days.size === DAYS.length
    ? `${length(first.durationMinutes)}, Every Day`
    : `${length(first.durationMinutes)}, ${dayList([...days])}${members.length === 1 && days.size === 1 ? ' Only' : ''}`;
  return withLocation(text, first);
}

/** One line of a list for a row of events: one event, or a group that repeats on several days. */
export function describeRow(row) {
  if (row.members.length === 1) {
    return describeEvent(row.first);
  }
  if (row.first.kind === 'FREE') {
    return describeFreeGroup(row.members);
  }
  const times = range(row.first.startMinute, endMinute(row.first));
  const text = row.members.length === DAYS.length
    ? `Every Day, ${times}`
    : `Every ${dayList(row.members.map(member => member.day))}, ${times}`;
  return withLocation(text, row.first);
}
