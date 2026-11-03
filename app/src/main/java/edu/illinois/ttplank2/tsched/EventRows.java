package edu.illinois.ttplank2.tsched;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The events as a person picks them from a list: one row for each event or, for an event that
 * repeats on several days, one row for the whole group, since deleting one of its days deletes
 * them all. Rows with a day come first in week order, Sunday first; events with no day yet follow.
 */
public final class EventRows {
    /** One line in the list. */
    public static final class Row {
        /** The member that comes first in the week, which stands for the row. */
        public final Event first;
        /** Every event in the row: just {@link #first} unless the row is a repeating group. */
        public final List<Event> members;

        Row(Event first, List<Event> members) {
            this.first = first;
            this.members = members;
        }
    }

    private EventRows() {
    }

    public static List<Row> build(List<Event> events) {
        List<Row> rows = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        for (Event event : events) {
            if (covered.contains(event.getId())) {
                continue;
            }
            List<Event> members = EventGroups.membersOf(events, event);
            Event first = members.get(0);
            for (Event member : members) {
                covered.add(member.getId());
                if (weekIndex(member) < weekIndex(first)) {
                    first = member;
                }
            }
            rows.add(new Row(first, members));
        }
        rows.sort(Comparator.comparingInt((Row row) -> weekIndex(row.first))
                .thenComparingInt(row -> row.first.getStartMinute())
                .thenComparing(row -> row.first.getName().toLowerCase(Locale.getDefault())));
        return rows;
    }

    /** The free events not yet placed, as rows: a group of free events is one row. */
    public static List<Row> unplacedFree(List<Event> events) {
        List<Event> unplaced = new ArrayList<>();
        for (Event event : events) {
            if (event.getKind() == Event.Kind.FREE && !event.isPlaced()) {
                unplaced.add(event);
            }
        }
        return build(unplaced);
    }

    /** Position in the week, Sunday first; events with no day yet go after every day. */
    private static int weekIndex(Event event) {
        DayOfWeek day = event.getDay();
        for (int i = 0; i < WeekDays.SUNDAY_FIRST.length; i++) {
            if (WeekDays.SUNDAY_FIRST[i] == day) {
                return i;
            }
        }
        return WeekDays.SUNDAY_FIRST.length;
    }
}
