/**
 * The names shown for the colors. Each color starts with its own name ("Red", "Teal"); the person can rename
 * any of them, and a blank name goes back to the original. Saved in the browser in the same one-line-per-color
 * format as the Android app (the color in hex, a tab, then the name).
 */
import { nameOf as originalName, currentShade } from './colors.js';

const KEY = 'tsched.colorNames';

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

/** The name to keep for what was typed: the text trimmed, or the original if it is blank. */
export function resolve(typed, original) {
  const trimmed = typed == null ? '' : String(typed).trim();
  return trimmed === '' ? original : trimmed;
}

/** One line per renamed color: its color in hex, a tab, then the name. */
export function encodeNames(names) {
  let text = '';
  for (const [color, name] of names) {
    text += `${(color >>> 0).toString(16)}\t${escape(name)}\n`;
  }
  return text;
}

/** Reads names back, skipping any line that is damaged. */
export function decodeNames(text) {
  const names = new Map();
  for (const line of text.split('\n')) {
    const fields = line.split('\t');
    if (fields.length !== 2 || !/^[0-9a-f]{1,8}$/i.test(fields[0])) {
      continue;
    }
    const name = unescape(fields[1]).trim();
    if (name !== '') {
      names.set(currentShade(parseInt(fields[0], 16) >>> 0), name);
    }
  }
  return names;
}

export class ColorNames {
  constructor(storage) {
    this.storage = storage;
    try {
      this.custom = decodeNames(storage.getItem(KEY) ?? '');
    } catch {
      this.custom = new Map();
    }
  }

  /** The person's name for the color, or its original name if they never changed it. */
  nameOf(color) {
    return this.custom.get(color) ?? originalName(color);
  }

  /** Whether the person gave the color a name of their own. */
  hasCustomName(color) {
    return this.custom.has(color);
  }

  /**
   * Renames each color in `typed` (an object from color number to text); a blank one restores that color's
   * original name. Returns false, changing nothing, if the names could not be saved.
   */
  renameAll(typed) {
    const updated = new Map(this.custom);
    for (const [key, text] of Object.entries(typed)) {
      const color = Number(key);
      const original = originalName(color);
      const name = resolve(text, original);
      if (name === original) {
        updated.delete(color);
      } else {
        updated.set(color, name);
      }
    }
    try {
      this.storage.setItem(KEY, encodeNames(updated));
    } catch {
      return false;
    }
    this.custom = updated;
    return true;
  }
}
