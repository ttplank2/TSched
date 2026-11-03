package edu.illinois.ttplank2.tsched;

/**
 * Spots a triple tap on one button. Each tap must come within the system's double-tap timeout (about
 * 300 ms) of the one before it; the third one is the triple tap. Taps for 0.3 seconds after a triple tap
 * are ignored, like after a double tap, so a fourth quick tap does not start a new count.
 */
final class TripleTap {
    /** How long taps are ignored after a triple tap: the same as after a double tap. */
    static final long COOLDOWN_MS = TapOrDoubleTap.COOLDOWN_MS;

    private final long windowMillis;
    private int count;
    private long lastTap = Long.MIN_VALUE;
    private long ignoreTapsUntil = Long.MIN_VALUE;

    /** {@code windowMillis} is how long after a tap the next one may come and still count. */
    TripleTap(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    /** Records a tap at {@code nowMillis} and says whether it was the third of a triple tap. */
    boolean tap(long nowMillis) {
        if (nowMillis < ignoreTapsUntil) {
            return false;
        }
        count = lastTap != Long.MIN_VALUE && nowMillis - lastTap <= windowMillis ? count + 1 : 1;
        lastTap = nowMillis;
        if (count < 3) {
            return false;
        }
        count = 0;
        ignoreTapsUntil = nowMillis + COOLDOWN_MS;
        return true;
    }
}
