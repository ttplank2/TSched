package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

public class EventRowsTest {
    private static List<String> names(List<EventRows.Row> rows) {
        List<String> names = new ArrayList<>();
        for (EventRows.Row row : rows) {
            names.add(row.first.getName());
        }
        return names;
    }

    @Test
    public void aRepeatingEventIsOneRowAndSoIsEachOtherEvent() {
        List<Event> events = EventGroups.saveFixed(new ArrayList<>(),
                Event.fixed("Class", "", DayOfWeek.MONDAY, 540, 600),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        events.add(Event.fixed("Gym", "", DayOfWeek.TUESDAY, 600, 660));

        List<EventRows.Row> rows = EventRows.build(events);

        assertEquals(2, rows.size());
        assertEquals(3, rows.get(0).members.size());
        assertEquals("Class", rows.get(0).first.getName());
        assertEquals(1, rows.get(1).members.size());
    }

    @Test
    public void rowsRunThroughTheWeekFromSundayThenFreeEvents() {
        List<Event> events = new ArrayList<>(List.of(
                Event.free("Read", "", 60),
                Event.fixed("Fri thing", "", DayOfWeek.FRIDAY, 540, 600),
                Event.fixed("Sun late", "", DayOfWeek.SUNDAY, 900, 960),
                Event.fixed("Sun early", "", DayOfWeek.SUNDAY, 480, 540),
                Event.fixed("Mon thing", "", DayOfWeek.MONDAY, 480, 540)));

        assertEquals(List.of("Sun early", "Sun late", "Mon thing", "Fri thing", "Read"),
                names(EventRows.build(events)));
    }

    @Test
    public void aRepeatingEventSitsWhereItsEarliestDayIs() {
        List<Event> events = EventGroups.saveFixed(new ArrayList<>(),
                Event.fixed("Class", "", DayOfWeek.MONDAY, 540, 600),
                EnumSet.of(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY));
        events.add(Event.fixed("Tue thing", "", DayOfWeek.TUESDAY, 540, 600));
        events.add(Event.fixed("Sun thing", "", DayOfWeek.SUNDAY, 540, 600));

        assertEquals(List.of("Sun thing", "Tue thing", "Class"), names(EventRows.build(events)));
        assertEquals(DayOfWeek.WEDNESDAY, EventRows.build(events).get(2).first.getDay());
    }

    @Test
    public void noEventsMeansNoRows() {
        assertEquals(0, EventRows.build(new ArrayList<>()).size());
    }

    @Test
    public void deletingRowsRemovesEveryDayOfARepeatingEventAndNothingElse() {
        List<Event> events = EventGroups.saveFixed(new ArrayList<>(),
                Event.fixed("Class", "", DayOfWeek.MONDAY, 540, 600),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        Event gym = Event.fixed("Gym", "", DayOfWeek.TUESDAY, 600, 660);
        Event lab = Event.fixed("Lab", "", DayOfWeek.THURSDAY, 600, 660);
        events.add(gym);
        events.add(lab);
        List<EventRows.Row> rows = EventRows.build(events);

        List<Event> chosen = new ArrayList<>();
        chosen.addAll(rows.get(0).members);
        chosen.addAll(rows.get(2).members);
        List<Event> left = EventGroups.withoutEvents(events, chosen);

        assertEquals(1, left.size());
        assertEquals("Gym", left.get(0).getName());
    }
}
