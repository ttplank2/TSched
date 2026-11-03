package edu.illinois.ttplank2.tsched;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

/**
 * The part of an event that falls on one day. Most events are one whole segment; an overnight
 * event (sleep from 11 PM to 7 AM) is two: the evening on the day it starts, and the morning on the
 * next day. Both segments belong to the same event, so tapping either opens it.
 */
public final class DaySegment {
    public final Event event;
    /** Minutes after midnight, on the day being shown, where this part starts. */
    public final int startMinute;
    /** Minutes after midnight, on the day being shown, where this part ends (at most 24 hours). */
    public final int endMinute;
    /** True for the evening part of an overnight event: it carries on into the next day. */
    public final boolean continuesAfter;
    /** True for the morning part of an overnight event: it began the day before. */
    public final boolean continuesFrom;

    DaySegment(Event event, int startMinute, int endMinute, boolean continuesAfter,
               boolean continuesFrom) {
        this.event = event;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.continuesAfter = continuesAfter;
        this.continuesFrom = continuesFrom;
    }

    /** The whole of {@code event}, for an event that stays inside its day. */
    static DaySegment whole(Event event) {
        return new DaySegment(event, event.getStartMinute(),
                Math.min(event.getEndMinute(), Event.MINUTES_PER_DAY), event.crossesMidnight(), false);
    }

    public int getDurationMinutes() {
        return endMinute - startMinute;
    }

    /** Every part of {@code events} that falls on {@code day}, including the end of last night's. */
    public static List<DaySegment> forDay(List<Event> events, DayOfWeek day) {
        List<DaySegment> segments = new ArrayList<>();
        for (Event event : events) {
            if (event.getDay() == day) {
                segments.add(whole(event));
            } else if (event.crossesMidnight() && event.getDay().plus(1) == day) {
                segments.add(new DaySegment(event, 0,
                        event.getEndMinute() - Event.MINUTES_PER_DAY, false, true));
            }
        }
        return segments;
    }
}
