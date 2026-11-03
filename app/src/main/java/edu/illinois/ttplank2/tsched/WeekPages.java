package edu.illinois.ttplank2.tsched;

import java.time.DayOfWeek;

/**
 * Maps calendar page numbers to weekdays. There are many pages that repeat Sunday to
 * Saturday, starting in the middle, so swiping past Saturday wraps around to Sunday in
 * either direction.
 */
public final class WeekPages {
    private static final int DAYS = 7;
    /** About 20 years of weeks in each direction: far more than anyone will swipe. */
    public static final int PAGE_COUNT = DAYS * 2000;

    private WeekPages() {
    }

    public static DayOfWeek dayAt(int page) {
        return WeekDays.SUNDAY_FIRST[page % DAYS];
    }

    /** The page to open on: the given weekday, in the middle of the range. */
    public static int startPage(DayOfWeek day) {
        int middleSunday = PAGE_COUNT / 2 - (PAGE_COUNT / 2) % DAYS;
        return middleSunday + indexInWeek(day);
    }

    /** The page showing {@code day} that is the fewest swipes away from {@code currentPage}. */
    public static int nearestPage(DayOfWeek day, int currentPage) {
        int sameWeek = currentPage - currentPage % DAYS + indexInWeek(day);
        int offset = sameWeek - currentPage;
        if (offset > DAYS / 2) {
            return sameWeek - DAYS;
        }
        if (offset < -DAYS / 2) {
            return sameWeek + DAYS;
        }
        return sameWeek;
    }

    /** Where {@code day} falls in a week that starts on Sunday: Sunday is 0, Saturday is 6. */
    private static int indexInWeek(DayOfWeek day) {
        for (int i = 0; i < DAYS; i++) {
            if (WeekDays.SUNDAY_FIRST[i] == day) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown day: " + day);
    }
}
