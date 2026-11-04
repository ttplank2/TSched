/** What the person chose in Settings, saved in the browser: the accent color and the calendar's start. */
import { DEFAULT, COLORS, currentShade } from './colors.js';

const KEY = 'tsched.settings';

export const DEFAULT_SETTINGS = Object.freeze({
  accent: DEFAULT,
  startMinute: 7 * 60,
  todayAtNow: false,
  lockVertical: true,
  vibration: false,
  sound: false,
});

/** Settings the web version cannot change: they always have their default, whatever an older save says. */
const LOCKED = Object.freeze({ lockVertical: true, vibration: false, sound: false });

/** The saved accent if it is a color of the wheel (an old darker shade becomes its current one); otherwise yellow. */
function validAccent(color) {
  const current = typeof color === 'number' ? currentShade(color) : null;
  return COLORS.includes(current) ? current : DEFAULT;
}

/** The saved settings, with the defaults for anything missing or unreadable. */
export function loadSettings(storage) {
  try {
    const saved = JSON.parse(storage.getItem(KEY) ?? '{}');
    return { ...DEFAULT_SETTINGS, ...saved, ...LOCKED, accent: validAccent(saved.accent ?? DEFAULT) };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

/** Saves the settings; false if the browser refused. */
export function saveSettings(storage, settings) {
  try {
    storage.setItem(KEY, JSON.stringify(settings));
    return true;
  } catch {
    return false;
  }
}
