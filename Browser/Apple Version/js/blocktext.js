/**
 * Decides how an event's name wraps inside its calendar block. Names break only between words. A one-hour
 * event gets two lines, longer events up to three, and shorter events one, never more than physically fit.
 * When space is short, the first name line comes first, then the time, then the second and third name lines.
 * Events under 30 minutes show no text. A word too long for a line, or a name that needs more lines than it
 * gets, ends with "...". (Ported from the Android app.)
 */
const ELLIPSIS = '...';
export const MIN_MINUTES_FOR_TEXT = 30;

/** How many name lines an event of this length may use, before checking what fits. */
export function nameLinesFor(durationMinutes) {
  if (durationMinutes < 60) {
    return 1;
  }
  return durationMinutes === 60 ? 2 : 3;
}

/** How many name lines a block shows, and whether its time line fits too. */
export function fit(durationMinutes, textHeight, nameLineHeight, timeLineHeight) {
  if (durationMinutes < MIN_MINUTES_FOR_TEXT || textHeight < nameLineHeight) {
    return { nameLines: 0, showTime: false };
  }
  let left = textHeight - nameLineHeight;
  const showTime = left >= timeLineHeight;
  if (showTime) {
    left -= timeLineHeight;
  }
  const extraLines = Math.min(nameLinesFor(durationMinutes) - 1, Math.floor(left / nameLineHeight));
  return { nameLines: 1 + extraLines, showTime };
}

function withEllipsis(text, width, measure) {
  const base = text.endsWith(ELLIPSIS) ? text.slice(0, -ELLIPSIS.length) : text;
  for (let end = base.length; end > 0; end--) {
    const candidate = base.slice(0, end).trim() + ELLIPSIS;
    if (measure(candidate) <= width) {
      return candidate;
    }
  }
  return ELLIPSIS;
}

/** The text if it fits, otherwise as much of it as fits followed by "...". */
export function cutToWidth(text, width, measure) {
  return measure(text) <= width ? text : withEllipsis(text, width, measure);
}

/** Wraps a name into at most `maxLines` lines no wider than `width`. Always returns at least one line. */
export function wrap(name, width, maxLines, measure) {
  const lines = [];
  const words = name.trim().split(/\s+/).filter(Boolean);
  let line = '';
  let next = 0;
  while (next < words.length && lines.length < maxLines) {
    const word = words[next];
    const candidate = line === '' ? word : `${line} ${word}`;
    if (measure(candidate) <= width) {
      line = candidate;
      next++;
    } else if (line === '') {
      lines.push(cutToWidth(word, width, measure));
      next++;
    } else {
      lines.push(line);
      line = '';
    }
  }
  if (line !== '' && lines.length < maxLines) {
    lines.push(line);
  }
  if (next < words.length && lines.length > 0) {
    lines[lines.length - 1] = withEllipsis(lines[lines.length - 1], width, measure);
  }
  return lines.length === 0 ? [''] : lines;
}
