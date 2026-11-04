package edu.illinois.ttplank2.tsched;

import android.content.Context;

import java.time.DayOfWeek;

/** The week as the app shows it to the user: Sunday first, with short day names. */
public final class WeekDays {
    /** The days from Sunday to Saturday, the order of the day buttons. */
    public static final DayOfWeek[] SUNDAY_FIRST = {
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
    };
    private static final int[] SHORT_NAMES = {
        R.string.day_sunday_short, R.string.day_monday_short, R.string.day_tuesday_short,
        R.string.day_wednesday_short, R.string.day_thursday_short, R.string.day_friday_short,
        R.string.day_saturday_short
    };

    private WeekDays() {
    }

    /** "Sun", "Mon", "Tues", "Wed", "Thurs", "Fri", or "Sat". */
    public static String shortName(Context context, DayOfWeek day) {
        for (int i = 0; i < SUNDAY_FIRST.length; i++) {
            if (SUNDAY_FIRST[i] == day) {
                return context.getString(SHORT_NAMES[i]);
            }
        }
        throw new IllegalArgumentException("Unknown day: " + day);
    }
}
