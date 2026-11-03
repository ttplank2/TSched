package edu.illinois.ttplank2.tsched;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ViewConfiguration;

/**
 * Tells a single tap from a double tap on one button. A single tap's action waits for the
 * system's double-tap timeout (about 300 ms); a second tap inside that time runs the
 * double-tap action instead. Taps for 0.3 seconds after a double tap are ignored, so a
 * triple tap doesn't open the single-tap action by accident.
 */
final class TapOrDoubleTap {
    /** How long taps are ignored after a double tap. */
    static final long COOLDOWN_MS = 300;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable onSingleTap;
    private final Runnable onDoubleTap;
    private Runnable pendingSingleTap;
    private long ignoreTapsUntil;

    TapOrDoubleTap(Runnable onSingleTap, Runnable onDoubleTap) {
        this.onSingleTap = onSingleTap;
        this.onDoubleTap = onDoubleTap;
    }

    void tap() {
        long now = SystemClock.uptimeMillis();
        if (now < ignoreTapsUntil) {
            return;
        }
        if (pendingSingleTap != null) {
            handler.removeCallbacks(pendingSingleTap);
            pendingSingleTap = null;
            ignoreTapsUntil = now + COOLDOWN_MS;
            onDoubleTap.run();
            return;
        }
        pendingSingleTap = () -> {
            pendingSingleTap = null;
            onSingleTap.run();
        };
        handler.postDelayed(pendingSingleTap, ViewConfiguration.getDoubleTapTimeout());
    }
}
