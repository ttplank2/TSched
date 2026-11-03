package edu.illinois.ttplank2.tsched;

import java.util.Locale;

/** Formats minutes-after-midnight and lengths for display. */
public final class TimeFormat {
    private TimeFormat() {
    }

    /** 0 becomes "12:00 AM", 870 becomes "2:30 PM", 1440 becomes "12:00 AM". */
    public static String clock(int minuteOfDay) {
        int hour24 = (minuteOfDay / 60) % 24;
        int minute = minuteOfDay % 60;
        int hour12 = hour24 % 12 == 0 ? 12 : hour24 % 12;
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, hour24 < 12 ? "AM" : "PM");
    }

    public static String range(int startMinute, int endMinute) {
        return clock(startMinute) + " – " + clock(endMinute);
    }

    /** "12 AM", "1 AM", and so on up to "11 PM": the labels down the side of the calendar. */
    public static String[] hourLabels() {
        String[] labels = new String[24];
        for (int hour = 0; hour < labels.length; hour++) {
            int hour12 = hour % 12 == 0 ? 12 : hour % 12;
            labels[hour] = hour12 + (hour < 12 ? " AM" : " PM");
        }
        return labels;
    }

    /** 90 becomes "1h 30m", 120 becomes "2h", 45 becomes "45m". */
    public static String length(int minutes) {
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) {
            return rest + "m";
        }
        return rest == 0 ? hours + "h" : hours + "h " + rest + "m";
    }
}
