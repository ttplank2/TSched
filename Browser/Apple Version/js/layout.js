/**
 * Works out what goes on one day: the part of each event that falls on it (an overnight event is two
 * parts), and which column each part's block sits in, so events that overlap in time sit side by side.
 */
import { DAYS, MINUTES_PER_DAY, crossesMidnight, endMinute } from './event.js';

/** The part of an event on one day, in minutes after midnight on that day. */
function segment(event, startMinute, endMin, continuesAfter, continuesFrom) {
  return { event, startMinute, endMinute: endMin, continuesAfter, continuesFrom, get durationMinutes() { return endMin - startMinute; } };
}

/** Every part of `events` that falls on `day`, including the end of last night's. */
export function segmentsForDay(events, day) {
  const dayIndex = DAYS.indexOf(day);
  const segments = [];
  for (const event of events) {
    if (event.day === day) {
      segments.push(segment(event, event.startMinute, Math.min(endMinute(event), MINUTES_PER_DAY), crossesMidnight(event), false));
    } else if (crossesMidnight(event) && (DAYS.indexOf(event.day) + 1) % 7 === dayIndex) {
      segments.push(segment(event, 0, endMinute(event) - MINUTES_PER_DAY, false, true));
    }
  }
  return segments;
}

/** Gives each part a column and says how many columns share its time. */
export function placeSegments(segments) {
  const sorted = [...segments].sort((a, b) => a.startMinute - b.startMinute || b.durationMinutes - a.durationMinutes);
  const placements = [];
  let group = [];
  let columnEnds = [];
  let groupEnd = 0;
  const finishGroup = () => {
    for (const placement of group) {
      placement.columnCount = columnEnds.length;
    }
    group = [];
  };
  for (const seg of sorted) {
    if (seg.startMinute >= groupEnd) {
      finishGroup();
      columnEnds = [];
    }
    let column = 0;
    while (column < columnEnds.length && columnEnds[column] > seg.startMinute) {
      column++;
    }
    columnEnds[column] = seg.endMinute;
    const placement = { ...seg, durationMinutes: seg.durationMinutes, column, columnCount: 1 };
    group.push(placement);
    placements.push(placement);
    groupEnd = Math.max(groupEnd, seg.endMinute);
  }
  finishGroup();
  return placements;
}
