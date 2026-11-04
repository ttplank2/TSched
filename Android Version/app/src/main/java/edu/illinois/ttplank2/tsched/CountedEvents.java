package edu.illinois.ttplank2.tsched;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * The countdown for counted events. A counted event ends once a week, and each time its end time
 * passes it loses a count; at zero it is deleted. Because the app only looks when it is opened, the
 * count is worked out from how many of those end times fell between the last time the app was
 * open and now: after a week away every event loses one, after two weeks it loses two.
 */
public final class CountedEvents {
    private CountedEvents() {
    }

    /**
     * {@code events} after the end times in the time from {@code last} (not included) up to
     * {@code now} (included) have passed: every counted event has lost a count for each, and one
     * with none left is gone. Events that didn't change are the very same objects.
     */
    public static List<Event> advance(List<Event> events, LocalDateTime last, LocalDateTime now) {
        List<Event> result = new ArrayList<>();
        for (Event event : events) {
            if (event.getKind() != Event.Kind.COUNTED) {
                result.add(event);
                continue;
            }
            int left = event.getCount() - endsBetween(event, last, now, event.getCount());
            if (left <= 0) {
                continue;
            }
            result.add(left == event.getCount() ? event : event.withCount(left));
        }
        return result;
    }

    /**
     * The first moment after {@code after} when any counted event in {@code events} ends, or null
     * if there is no counted event. The app uses it to wake up and count an event down just as it
     * ends while the app is open.
     */
    public static LocalDateTime nextEnd(List<Event> events, LocalDateTime after) {
        LocalDateTime next = null;
        for (Event event : events) {
            if (event.getKind() != Event.Kind.COUNTED) {
                continue;
            }
            int endOffset = endOffsetInWeek(event);
            // A week back and two ahead cover an overnight event and the next one after it.
            LocalDate monday = after.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
            for (int week = 0; week < 4; week++) {
                LocalDateTime end = monday.plusWeeks(week).atStartOfDay().plusMinutes(endOffset);
                if (end.isAfter(after)) {
                    if (next == null || end.isBefore(next)) {
                        next = end;
                    }
                    break;
                }
            }
        }
        return next;
    }

    /** Minutes from the start of the week (Monday) to when {@code event} ends. */
    private static int endOffsetInWeek(Event event) {
        return (event.getDay().getValue() - 1) * Event.MINUTES_PER_DAY + event.getEndMinute();
    }

    /**
     * How many times {@code event} ends after {@code last} up to {@code now}, counting no more
     * than {@code limit}.
     */
    static int endsBetween(Event event, LocalDateTime last, LocalDateTime now, int limit) {
        if (!now.isAfter(last)) {
            return 0;
        }
        // An event that runs past midnight on Sunday ends early the next Monday, which this still
        // reaches by adding minutes to the Monday that began its own week.
        int endOffset = endOffsetInWeek(event);
        // One week back, so an overnight event that began the week before `last` is found too.
        LocalDate monday = last.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        int ends = 0;
        for (; !monday.atStartOfDay().isAfter(now) && ends < limit; monday = monday.plusWeeks(1)) {
            LocalDateTime end = monday.atStartOfDay().plusMinutes(endOffset);
            if (end.isAfter(last) && !end.isAfter(now)) {
                ends++;
            }
        }
        return ends;
    }
}
