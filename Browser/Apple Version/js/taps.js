/**
 * Tells a single tap from a double tap on one button. A single tap's action waits for the double-tap time
 * (about 300 ms); a second tap inside that time runs the double-tap action instead. Taps for 0.3 seconds after a
 * double tap are ignored, so a triple tap does not open the single-tap action by accident. (Like the Android app.)
 */
export const COOLDOWN_MS = 300;
export const DOUBLE_TAP_MS = 300;

const realTime = {
  now: () => performance.now(),
  setTimeout: (fn, ms) => globalThis.setTimeout(fn, ms),
  clearTimeout: id => globalThis.clearTimeout(id),
};

export class TapOrDoubleTap {
  /** `time` and `windowMs` can be replaced in tests; the defaults are the real clock and 300 ms. */
  constructor(onSingleTap, onDoubleTap, time = realTime, windowMs = DOUBLE_TAP_MS) {
    this.onSingleTap = onSingleTap;
    this.onDoubleTap = onDoubleTap;
    this.time = time;
    this.windowMs = windowMs;
    this.pending = null;
    this.ignoreTapsUntil = -Infinity;
  }

  tap() {
    const now = this.time.now;
    const current = typeof now === 'function' ? now() : now;
    if (current < this.ignoreTapsUntil) {
      return;
    }
    if (this.pending !== null) {
      this.time.clearTimeout(this.pending);
      this.pending = null;
      this.ignoreTapsUntil = current + COOLDOWN_MS;
      this.onDoubleTap();
      return;
    }
    this.pending = this.time.setTimeout(() => {
      this.pending = null;
      this.onSingleTap();
    }, this.windowMs);
  }
}
