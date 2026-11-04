package edu.illinois.ttplank2.tsched;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orders the events of a suggested placement for reading: the days of a group of free events
 * are listed together, Sunday first, and the groups and lone events are sorted by name instead of
 * in whatever order the placer found them.
 */
public final class PlacementBlocks {
    private PlacementBlocks() {
    }

    /**
     * One list per group of free events, or per event that stands alone. Each list is in week
     * order, Sunday first; the lists are sorted by the event name, then by their first day.
     */
    public static List<List<Event>> of(List<Event> events) {
        Map<String, List<Event>> byGroup = new LinkedHashMap<>();
        List<List<Event>> blocks = new ArrayList<>();
        for (Event event : events) {
            if (event.isGrouped()) {
                byGroup.computeIfAbsent(event.getGroupId(), id -> new ArrayList<>()).add(event);
            } else {
                List<Event> alone = new ArrayList<>();
                alone.add(event);
                blocks.add(alone);
            }
        }
        blocks.addAll(byGroup.values());
        for (List<Event> block : blocks) {
            block.sort(Comparator.comparingInt((Event event) -> weekIndex(event.getDay()))
                    .thenComparingInt(Event::getStartMinute));
        }
        blocks.sort(Comparator
                .comparing((List<Event> block) -> block.get(0).getName().toLowerCase(Locale.getDefault()))
                .thenComparingInt(block -> weekIndex(block.get(0).getDay())));
        return blocks;
    }

    /** Position in the week, Sunday first. */
    private static int weekIndex(DayOfWeek day) {
        for (int i = 0; i < WeekDays.SUNDAY_FIRST.length; i++) {
            if (WeekDays.SUNDAY_FIRST[i] == day) {
                return i;
            }
        }
        return WeekDays.SUNDAY_FIRST.length;
    }
}
