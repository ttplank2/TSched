/**
 * The colors an event can be, with their names. The same ten as the Android app's color wheel, as
 * ARGB numbers like 0xFFFFC93C. Events with no color chosen show the default yellow.
 */
export const DEFAULT = 0xFFFFC93C;

const GREEN = 0xFF68BC6C;
const TEAL = 0xFF2BBDAF;
const BLUE = 0xFF5AB1F6;
const PURPLE = 0xFFCF96D9;

export const COLORS = [
  0xFFEF5350, 0xFFFF8A3D, DEFAULT, 0xFFC6E05A,
  GREEN, TEAL, BLUE, PURPLE,
  0xFFEC407A, 0xFFF48FB1,
];

export const NAMES = [
  'Red', 'Orange', 'Yellow', 'Lime',
  'Green', 'Teal', 'Blue', 'Purple',
  'Magenta', 'Pink',
];

/** The darker shades Green, Teal, Blue and Purple used to have, and the brighter shade of each now. */
const RECOLORED = [
  [0xFF66BB6A, GREEN],
  [0xFF26A69A, TEAL],
  [0xFF42A5F5, BLUE],
  [0xFFAB47BC, PURPLE],
];

/** Colors that used to be on the wheel and the color each one became. */
const REPLACEMENTS = [
  [0xFF4DD0E1, TEAL],
  [0xFF5C6BC0, PURPLE],
  [0xFFA1887F, DEFAULT],
  [0xFFE0E0E0, DEFAULT],
  [0xFF90A4AE, DEFAULT],
];

/** The brighter shade now used for an old darker color; any other color comes back unchanged. */
export function currentShade(color) {
  const found = RECOLORED.find(([old]) => old === color);
  return found ? found[1] : color;
}

/** The color if it is on the wheel; otherwise the one that replaced it, or the default yellow. */
export function onWheel(color) {
  if (COLORS.includes(color)) {
    return color;
  }
  const shade = currentShade(color);
  if (shade !== color) {
    return shade;
  }
  const found = REPLACEMENTS.find(([old]) => old === color);
  return found ? found[1] : DEFAULT;
}

/** The color's name, such as "Red", or "Custom" for a color not on the wheel. */
export function nameOf(color) {
  const index = COLORS.indexOf(color);
  return index < 0 ? 'Custom' : NAMES[index];
}

/** An ARGB number as a CSS color, such as '#ffc93c'. */
export function hex(color) {
  return '#' + (color & 0xFFFFFF).toString(16).padStart(6, '0');
}
