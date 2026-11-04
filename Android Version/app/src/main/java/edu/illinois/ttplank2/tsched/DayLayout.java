package edu.illinois.ttplank2.tsched;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides where each fixed event's block goes in a day's column. Events that overlap in
 * time are split into side-by-side columns so none hides another.
 */
public final class DayLayout {
    /** One event's block: which column it sits in, out of how many share its time. */
    public static final class Placement {
        public final Event event;
        /** Where this block starts and ends on the day, which for an overnight event is only its part of it. */
        public final int startMinute;
        public final int endMinute;
        /** Whether this block is the evening or morning part of an overnight event. */
        public final boolean continuesAfter;
        public final boolean continuesFrom;
        public final int column;
        private int columnCount;

        Placement(DaySegment segment, int column) {
            this.event = segment.event;
            this.startMinute = segment.startMinute;
            this.endMinute = segment.endMinute;
            this.continuesAfter = segment.continuesAfter;
            this.continuesFrom = segment.continuesFrom;
            this.column = column;
        }

        public int getColumnCount() {
            return columnCount;
        }
    }

    private DayLayout() {
    }

    /** Places events that each stay inside the day. */
    public static List<Placement> place(List<Event> dayEvents) {
        List<DaySegment> segments = new ArrayList<>();
        for (Event event : dayEvents) {
            segments.add(DaySegment.whole(event));
        }
        return placeSegments(segments);
    }

    /** Places the parts of events that fall on one day. */
    public static List<Placement> placeSegments(List<DaySegment> daySegments) {
        List<DaySegment> sorted = new ArrayList<>(daySegments);
        sorted.sort(Comparator.comparingInt((DaySegment segment) -> segment.startMinute)
                .thenComparing(Comparator.comparingInt(DaySegment::getDurationMinutes).reversed()));

        List<Placement> placements = new ArrayList<>();
        List<Placement> group = new ArrayList<>();
        List<Integer> columnEnds = new ArrayList<>();
        int groupEnd = 0;
        for (DaySegment segment : sorted) {
            if (segment.startMinute >= groupEnd) {
                finishGroup(group, columnEnds.size());
                columnEnds.clear();
            }
            int column = 0;
            while (column < columnEnds.size() && columnEnds.get(column) > segment.startMinute) {
                column++;
            }
            if (column == columnEnds.size()) {
                columnEnds.add(segment.endMinute);
            } else {
                columnEnds.set(column, segment.endMinute);
            }
            Placement placement = new Placement(segment, column);
            group.add(placement);
            placements.add(placement);
            groupEnd = Math.max(groupEnd, segment.endMinute);
        }
        finishGroup(group, columnEnds.size());
        return placements;
    }

    private static void finishGroup(List<Placement> group, int columnCount) {
        for (Placement placement : group) {
            placement.columnCount = columnCount;
        }
        group.clear();
    }
}
