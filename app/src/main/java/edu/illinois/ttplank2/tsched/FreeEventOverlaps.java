package edu.illinois.ttplank2.tsched;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds free events that were already placed on the calendar and that a newly saved fixed event
 * now sits on top of. Only placed free events have a time to overlap; unplaced ones don't.
 */
public final class FreeEventOverlaps {
    /** A placed free event and the fixed (or counted) event that overlaps it. */
    public static final class Overlap {
        public final Event free;
        public final Event fixed;

        Overlap(Event free, Event fixed) {
            this.free = free;
            this.fixed = fixed;
        }
    }

    private FreeEventOverlaps() {
    }

    /**
     * Every overlap between a placed free event in {@code events} and one of {@code changed}, the
     * fixed or counted events that were just added or edited. An overnight event also counts on
     * the morning after it starts.
     */
    public static List<Overlap> find(List<Event> events, List<Event> changed) {
        List<Overlap> overlaps = new ArrayList<>();
        for (Event free : events) {
            if (!free.isPlaced()) {
                continue;
            }
            for (Event fixed : changed) {
                if (fixed.getKind() != Event.Kind.FIXED && fixed.getKind() != Event.Kind.COUNTED) {
                    continue;
                }
                if (overlapsOnItsDay(free, fixed)) {
                    overlaps.add(new Overlap(free, fixed));
                }
            }
        }
        return overlaps;
    }

    private static boolean overlapsOnItsDay(Event free, Event fixed) {
        DayOfWeek day = free.getDay();
        for (DaySegment segment : DaySegment.forDay(List.of(fixed), day)) {
            if (segment.startMinute < free.getEndMinute() && segment.endMinute > free.getStartMinute()) {
                return true;
            }
        }
        return false;
    }
}
