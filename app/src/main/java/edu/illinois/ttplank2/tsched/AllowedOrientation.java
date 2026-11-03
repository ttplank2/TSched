package edu.illinois.ttplank2.tsched;

import android.content.pm.ActivityInfo;

/**
 * Picks the screen orientation for the calendar from the angle the phone's sensor reports (0 is
 * upright, 90 is turned a quarter turn clockwise, 270 counterclockwise). The calendar turns to
 * upright and to either side; when the phone is upside down, or between two of those, the screen
 * stays however it was, so there is no upside-down screen at all.
 */
public final class AllowedOrientation {
    /** How far from exactly upright or exactly on its side the phone may be and still count. */
    private static final int SLACK_DEGREES = 40;
    /** The angle sensors give when they can't tell. */
    private static final int UNKNOWN = -1;

    private AllowedOrientation() {
    }

    /**
     * The orientation to show for a phone at {@code angle}: one of the {@link ActivityInfo}
     * portrait, landscape or reverse landscape values, or {@code previous} if the phone is upside
     * down, between two orientations, or the angle is unknown.
     */
    public static int forAngle(int angle, int previous) {
        if (angle == UNKNOWN) {
            return previous;
        }
        if (distance(angle, 0) <= SLACK_DEGREES) {
            return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        if (distance(angle, 270) <= SLACK_DEGREES) {
            return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
        if (distance(angle, 90) <= SLACK_DEGREES) {
            return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        }
        return previous;
    }

    /** How many degrees apart two angles are, going the short way round the circle. */
    private static int distance(int angle, int target) {
        int apart = Math.abs(angle - target) % 360;
        return Math.min(apart, 360 - apart);
    }
}
