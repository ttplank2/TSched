/** The numbers behind the color wheel: how a row is bent around the drum, and where the wheel settles. */
import { COLORS } from './colors.js';

export const ROW_HEIGHT = 48;
export const DRUM_STEP_DEGREES = 25;

/**
 * How to draw a row that is `rowsFromMiddle` rows from the middle of the wheel (negative is above): the row
 * is flattened, shrunk and faded as it turns away, and pulled in toward the middle, as on a drum.
 * The same formulas as the Android app.
 */
export function shapeRow(rowsFromMiddle) {
  const step = (DRUM_STEP_DEGREES * Math.PI) / 180;
  const radius = ROW_HEIGHT / step;
  const quarterTurn = Math.PI / 2;
  const angle = Math.max(-quarterTurn, Math.min(quarterTurn, rowsFromMiddle * step));
  const flatten = Math.cos(angle);
  return {
    translateY: radius * Math.sin(angle) - rowsFromMiddle * ROW_HEIGHT,
    scaleY: flatten,
    scaleX: 0.8 + 0.2 * flatten,
    opacity: flatten,
  };
}

/** The place in a repeating list of `size` for any whole number, forward or backward. */
export function wrapIndex(index, size) {
  return ((index % size) + size) % size;
}

/** The whole row the wheel settles on from a spot between rows. */
export function nearestRow(position) {
  return Math.floor(position + 0.5);
}

/** The row the wheel opens on: the chosen color's, else the accent color's, else the first. */
export function startRowFor(selected, accent) {
  const chosen = COLORS.indexOf(selected);
  if (chosen >= 0) {
    return chosen;
  }
  return Math.max(0, COLORS.indexOf(accent));
}
