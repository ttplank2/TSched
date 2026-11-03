package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

public class PlacementBlocksTest {
    private static List<Event> placedGroup(String name, DayOfWeek... days) {
        List<Event> saved = EventGroups.saveFree(new ArrayList<>(),
                Event.free(name, "", 60), EnumSet.copyOf(List.of(days)));
        List<Event> placed = new ArrayList<>();
        for (Event member : saved) {
            placed.add(member.placedAt(member.getAllowedDays().iterator().next(), 10 * 60));
        }
        return placed;
    }

    @Test
    public void aGroupsDaysAreListedFromSundayToSaturday() {
        List<Event> shuffled = placedGroup("Study", DayOfWeek.SATURDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY);
        java.util.Collections.reverse(shuffled);

        List<List<Event>> blocks = PlacementBlocks.of(shuffled);

        assertEquals(1, blocks.size());
        assertEquals(DayOfWeek.SUNDAY, blocks.get(0).get(0).getDay());
        assertEquals(DayOfWeek.WEDNESDAY, blocks.get(0).get(1).getDay());
        assertEquals(DayOfWeek.SATURDAY, blocks.get(0).get(2).getDay());
    }

    @Test
    public void groupsAndLoneEventsAreSortedByName() {
        List<Event> placed = new ArrayList<>(placedGroup("Zumba", DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        placed.add(Event.free("apple", "", 30).placedAt(DayOfWeek.FRIDAY, 9 * 60));
        placed.addAll(placedGroup("Math", DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));

        List<List<Event>> blocks = PlacementBlocks.of(placed);

        assertEquals(3, blocks.size());
        assertEquals("apple", blocks.get(0).get(0).getName());
        assertEquals("Math", blocks.get(1).get(0).getName());
        assertEquals("Zumba", blocks.get(2).get(0).getName());
    }
}
