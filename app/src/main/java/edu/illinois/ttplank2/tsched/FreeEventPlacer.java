package edu.illinois.ttplank2.tsched;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Suggests where to put unplaced free events: in open time between the week's fixed and
 * already-placed events, inside the hours the user allows, on days each event allows.
 *
 * <p>Events with fewer allowed days and longer lengths go first, since they are hardest to fit.
 * Each goes on the allowed day with the most open time left, so the week stays balanced.
 */
public final class FreeEventPlacer {
    /** The outcome: events with their suggested day and time, and those that did not fit. */
    public static final class Suggestion {
        public final List<Event> placed = new ArrayList<>();
        public final List<Event> unfitted = new ArrayList<>();
    }

    private FreeEventPlacer() {
    }

    /**
     * @param week         every saved event; fixed and placed ones are treated as busy time
     * @param windowStart  earliest minute of the day an event may start
     * @param windowEnd    minute of the day by which events must end
     * @param random       breaks ties between equally good days and gaps, so "Try another"
     *                     can give a different layout; pass a fixed seed for repeatable results
     */
    public static Suggestion suggest(List<Event> week, int windowStart, int windowEnd, Random random) {
        if (windowStart < 0 || windowEnd > Event.MINUTES_PER_DAY || windowStart >= windowEnd) {
            throw new IllegalArgumentException("The end time must be after the start time.");
        }
        Map<DayOfWeek, List<int[]>> busy = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            busy.put(day, new ArrayList<>());
        }
        List<Event> toPlace = new ArrayList<>();
        for (Event event : week) {
            if (event.getDay() != null) {
                // An overnight event also keeps the start of the next day busy.
                int endToday = Math.min(event.getEndMinute(), Event.MINUTES_PER_DAY);
                busy.get(event.getDay()).add(new int[] {event.getStartMinute(), endToday});
                if (event.crossesMidnight()) {
                    busy.get(event.getDay().plus(1)).add(
                            new int[] {0, event.getEndMinute() - Event.MINUTES_PER_DAY});
                }
            } else {
                toPlace.add(event);
            }
        }

        Collections.shuffle(toPlace, random);
        toPlace.sort(Comparator.comparingInt(FreeEventPlacer::allowedDayCount)
                .thenComparing(Comparator.comparingInt(Event::getDurationMinutes).reversed()));

        Suggestion suggestion = new Suggestion();
        for (Event event : toPlace) {
            List<DayOfWeek> days = new ArrayList<>();
            for (DayOfWeek day : DayOfWeek.values()) {
                if (event.allowsDay(day)) {
                    days.add(day);
                }
            }
            Collections.shuffle(days, random);
            days.sort(Comparator.comparingInt(
                    (DayOfWeek day) -> openMinutes(busy.get(day), windowStart, windowEnd)).reversed());

            Event placed = null;
            for (DayOfWeek day : days) {
                List<Integer> starts = fittingStarts(busy.get(day), windowStart, windowEnd,
                        event.getDurationMinutes());
                if (!starts.isEmpty()) {
                    int start = starts.get(random.nextInt(starts.size()));
                    placed = event.placedAt(day, start);
                    busy.get(day).add(new int[] {start, start + event.getDurationMinutes()});
                    break;
                }
            }
            if (placed != null) {
                suggestion.placed.add(placed);
            } else {
                suggestion.unfitted.add(event);
            }
        }
        return suggestion;
    }

    /** The week with every placed free event taken off the calendar; fixed events are unchanged. */
    public static List<Event> recallAll(List<Event> week) {
        List<Event> result = new ArrayList<>();
        for (Event event : week) {
            result.add(event.isPlaced() ? event.unplaced() : event);
        }
        return result;
    }

    /** How many free events in the week are placed on the calendar. */
    public static int countPlaced(List<Event> week) {
        int count = 0;
        for (Event event : week) {
            if (event.isPlaced()) {
                count++;
            }
        }
        return count;
    }

    private static int allowedDayCount(Event event) {
        int count = 0;
        for (DayOfWeek day : DayOfWeek.values()) {
            if (event.allowsDay(day)) {
                count++;
            }
        }
        return count;
    }

    /** The open gaps of a day within the window, as [start, end) pairs. */
    private static List<int[]> gaps(List<int[]> busy, int windowStart, int windowEnd) {
        List<int[]> sorted = new ArrayList<>(busy);
        sorted.sort(Comparator.comparingInt(interval -> interval[0]));
        List<int[]> gaps = new ArrayList<>();
        int cursor = windowStart;
        for (int[] interval : sorted) {
            if (interval[0] > cursor) {
                gaps.add(new int[] {cursor, Math.min(interval[0], windowEnd)});
            }
            cursor = Math.max(cursor, interval[1]);
            if (cursor >= windowEnd) {
                break;
            }
        }
        if (cursor < windowEnd) {
            gaps.add(new int[] {cursor, windowEnd});
        }
        return gaps;
    }

    private static int openMinutes(List<int[]> busy, int windowStart, int windowEnd) {
        int total = 0;
        for (int[] gap : gaps(busy, windowStart, windowEnd)) {
            total += gap[1] - gap[0];
        }
        return total;
    }

    /**
     * Start times that fit: the beginning of each gap long enough for the event. Starting
     * at a gap's edge keeps the rest of the gap in one piece for other events.
     */
    private static List<Integer> fittingStarts(List<int[]> busy, int windowStart, int windowEnd,
                                               int length) {
        List<Integer> starts = new ArrayList<>();
        for (int[] gap : gaps(busy, windowStart, windowEnd)) {
            if (gap[1] - gap[0] >= length) {
                starts.add(gap[0]);
            }
        }
        return starts;
    }
}
