/**
 * The events as a person picks them from a list: one row for each event or, for an event that repeats on several
 * days, one row for the whole group, since deleting one of its days deletes them all. A port of EventRows.java.
 */
import { DAYS, isGrouped } from './event.js';

const SHORT_DAYS = ['Sun', 'Mon', 'Tues', 'Wed', 'Thurs', 'Fri', 'Sat'];
const weekIndex = event => (event.day == null ? DAYS.length : DAYS.indexOf(event.day));

/** Every event that belongs with this one: its whole group, or just itself. */
export const membersOf = (events, event) =>
  (isGrouped(event) ? events.filter(other => other.groupId === event.groupId) : [event]);

/** Rows with a day first in week order, Sunday first; events with no day yet follow. Each row: { first, members }. */
export function buildRows(events) {
  const rows = [];
  const covered = new Set();
  for (const event of events) {
    if (covered.has(event.id)) {
      continue;
    }
    const members = membersOf(events, event);
    let first = members[0];
    for (const member of members) {
      covered.add(member.id);
      if (weekIndex(member) < weekIndex(first)) {
        first = member;
      }
    }
    rows.push({ first, members });
  }
  return rows.sort((a, b) => weekIndex(a.first) - weekIndex(b.first)
    || a.first.startMinute - b.first.startMinute
    || a.first.name.toLowerCase().localeCompare(b.first.name.toLowerCase()));
}

/** The free events not yet placed, as rows: a group of free events is one row. */
export const unplacedRows = events => buildRows(events.filter(event => event.kind === 'FREE' && event.day == null));

/** Days as "Sun · Mon · Tues", Sunday first. */
export const dayList = days => DAYS.filter(day => days.includes(day)).map(day => SHORT_DAYS[DAYS.indexOf(day)]).join(' · ');

/**
 * One list per group of free events, or per event that stands alone. Each list is in week order, Sunday first; the
 * lists are sorted by the event name, then by their first day. For the Suggested Placement popup.
 */
export function placementBlocks(events) {
  const byGroup = new Map();
  const blocks = [];
  for (const event of events) {
    if (isGrouped(event)) {
      byGroup.set(event.groupId, [...(byGroup.get(event.groupId) ?? []), event]);
    } else {
      blocks.push([event]);
    }
  }
  blocks.push(...byGroup.values());
  for (const block of blocks) {
    block.sort((a, b) => weekIndex(a) - weekIndex(b) || a.startMinute - b.startMinute);
  }
  return blocks.sort((a, b) => a[0].name.toLowerCase().localeCompare(b[0].name.toLowerCase()) || weekIndex(a[0]) - weekIndex(b[0]));
}
