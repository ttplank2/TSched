/**
 * Saving an event on several days as a group that is edited as one, like the Android app's EventGroups: days already
 * in the group keep their events, new days get new events, and days no longer chosen lose theirs.
 */
import { DAYS, NO_GROUP, NO_TIME, newId, fixed, counted, free, placedAt, withColor, withGroupId, isGrouped } from './event.js';

/** The events in `event`'s group, or just `event` if it stands alone. */
export const membersOf = (events, event) => (isGrouped(event) ? events.filter(saved => saved.groupId === event.groupId) : [event]);

/** The days a group of events is on: each fixed or placed event's own day, and an unplaced free event's allowed days. */
export function daysOf(members) {
  const days = new Set();
  for (const member of members) {
    (member.day == null ? member.allowedDays : [member.day]).forEach(day => days.add(day));
  }
  return DAYS.filter(day => days.has(day));
}

/** The id of the member on `day` (placed there, or limited to only it), or null if there is none. */
function idOnDay(members, day) {
  const found = members.find(member => member.day === day || (member.day == null && member.allowedDays.length === 1 && member.allowedDays[0] === day));
  return found ? found.id : null;
}

function groupIdFor(original, days) {
  if (original && isGrouped(original)) {
    return original.groupId;
  }
  return days.length > 1 ? newId() : NO_GROUP;
}

/**
 * `events` with the fixed or counted event `details` (its name, location, times, color and id; its own day is
 * ignored) saved on each of `days`. If its id is already saved, that event and its group are replaced. For a counted
 * event, `keepCounts` keeps the count each existing day has left instead of giving every day `details.count`.
 */
export function saveFixedGroup(events, details, days, keepCounts = false) {
  if (days.length === 0) {
    throw new Error('Pick a day for the event.');
  }
  const original = events.find(saved => saved.id === details.id);
  const members = original ? membersOf(events, original) : [];
  const groupId = groupIdFor(original, days);
  const result = events.filter(saved => !members.some(member => member.id === saved.id));
  for (const day of DAYS.filter(d => days.includes(d))) {
    const id = idOnDay(members, day) ?? newId();
    const fields = { ...details, id, day, groupId, endMinute: null };
    let dayEvent;
    if (details.kind === 'COUNTED') {
      const kept = keepCounts ? members.find(member => member.id === id && member.kind === 'COUNTED')?.count ?? 0 : 0;
      dayEvent = counted({ ...fields, count: kept > 0 ? kept : details.count });
    } else {
      dayEvent = fixed(fields);
    }
    result.push(withGroupId(withColor(dayEvent, details.color), groupId));
  }
  return result;
}

/**
 * `events` with the free event `details` saved once on each of `days`: a group of free events, each placeable only on
 * its own day. With no days it is one event that may go on any day. If its id is already saved, that event and its
 * group are replaced; a member placed on a day still chosen stays where it was if that spot still fits.
 */
export function saveFreeGroup(events, details, days) {
  const original = events.find(saved => saved.id === details.id);
  const members = original ? membersOf(events, original) : [];
  const result = events.filter(saved => !members.some(member => member.id === saved.id));
  const unplacedFields = { ...details, day: null, startMinute: NO_TIME };
  const keepPlacement = (event, member) => {
    if (member && member.day != null && (event.allowedDays.length === 0 || event.allowedDays.includes(member.day))
        && member.startMinute + event.durationMinutes <= 24 * 60) {
      return placedAt(event, member.day, member.startMinute);
    }
    return event;
  };
  if (days.length === 0) {
    const id = original ? original.id : newId();
    result.push(withColor(keepPlacement(free({ ...unplacedFields, id, allowedDays: [], groupId: NO_GROUP }), original), details.color));
    return result;
  }
  const groupId = groupIdFor(original, days);
  for (const day of DAYS.filter(d => days.includes(d))) {
    const id = idOnDay(members, day) ?? newId();
    const member = members.find(m => m.id === id);
    const dayEvent = keepPlacement(free({ ...unplacedFields, id, allowedDays: [day], groupId }), member);
    result.push(withGroupId(withColor(dayEvent, details.color), groupId));
  }
  return result;
}
