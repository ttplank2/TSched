package edu.illinois.ttplank2.tsched;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Works out which event notification comes next. Events repeat every week, so each one is the next
 * time its day and start come round. Every event that notifies does so the same number of minutes
 * before it starts, and only events of the chosen colors notify. This holds no Android code, so it
 * can be tested on its own.
 */
final class NotificationPlanner {
    /** How many days ahead to look: a week of repeats, and a day more for a long lead time. */
    private static final int DAYS_AHEAD = 9;

    /** The next moment to notify, and the events (one or more) that notify then. */
    static final class Next {
        final LocalDateTime notifyAt;
        final List<Event> events;

        Next(LocalDateTime notifyAt, List<Event> events) {
            this.notifyAt = notifyAt;
            this.events = events;
        }
    }

    private NotificationPlanner() {
    }

    /**
     * The earliest notification strictly after {@code now}, or null if no event will notify.
     * Events that start at the same moment notify together.
     */
    static Next next(Collection<Event> events, Set<Integer> chosenColors, int leadMinutes,
                     LocalDateTime now) {
        LocalDateTime earliest = null;
        List<Event> due = new ArrayList<>();
        for (Event event : events) {
            if (!notifies(event, chosenColors)) {
                continue;
            }
            LocalDateTime notifyAt = nextStart(event.getDay(), event.getStartMinute(), leadMinutes, now)
                    .minusMinutes(leadMinutes);
            if (earliest == null || notifyAt.isBefore(earliest)) {
                earliest = notifyAt;
                due.clear();
                due.add(event);
            } else if (notifyAt.equals(earliest)) {
                due.add(event);
            }
        }
        return earliest == null ? null : new Next(earliest, due);
    }

    /** Whether {@code event} has a day and time and is one of the chosen colors. */
    static boolean notifies(Event event, Set<Integer> chosenColors) {
        boolean timed = event.getDay() != null && event.getStartMinute() != Event.NO_TIME;
        return timed && chosenColors.contains(EventColors.currentShade(event.getColor()));
    }

    /** The start of the next showing of an event on {@code day} whose notification is still ahead of {@code now}. */
    private static LocalDateTime nextStart(DayOfWeek day, int startMinute, int leadMinutes,
                                           LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        for (int ahead = 0; ahead <= DAYS_AHEAD + 7; ahead++) {
            LocalDate candidate = date.plusDays(ahead);
            if (candidate.getDayOfWeek() != day) {
                continue;
            }
            LocalDateTime start = candidate.atStartOfDay().plusMinutes(startMinute);
            if (start.minusMinutes(leadMinutes).isAfter(now)) {
                return start;
            }
        }
        // A lead time is at most a day, so a week of repeats always reaches one; this is never used.
        return date.plusDays(DAYS_AHEAD + 7).atStartOfDay();
    }
}
