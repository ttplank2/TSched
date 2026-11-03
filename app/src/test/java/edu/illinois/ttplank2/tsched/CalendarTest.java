package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;

import java.time.DayOfWeek;
import java.util.List;

import org.junit.Test;

public class CalendarTest {
    @Test
    public void calendarOpensOnTheRequestedDay() {
        for (DayOfWeek day : DayOfWeek.values()) {
            assertEquals(day, WeekPages.dayAt(WeekPages.startPage(day)));
        }
    }

    @Test
    public void swipingPastSaturdayWrapsToSunday() {
        int saturday = WeekPages.startPage(DayOfWeek.SATURDAY);
        assertEquals(DayOfWeek.SUNDAY, WeekPages.dayAt(saturday + 1));
        int sunday = WeekPages.startPage(DayOfWeek.SUNDAY);
        assertEquals(DayOfWeek.SATURDAY, WeekPages.dayAt(sunday - 1));
        assertEquals(DayOfWeek.MONDAY, WeekPages.dayAt(sunday + 1));
    }

    @Test
    public void snapToDayTakesTheShortestWay() {
        int tuesday = WeekPages.startPage(DayOfWeek.TUESDAY);
        // Saturday is 3 swipes back from Tuesday (wrapping), rather than 4 forward.
        assertEquals(tuesday - 3, WeekPages.nearestPage(DayOfWeek.SATURDAY, tuesday));
        int saturday = WeekPages.startPage(DayOfWeek.SATURDAY);
        // Monday is 2 swipes forward from Saturday, rather than 5 back.
        assertEquals(saturday + 2, WeekPages.nearestPage(DayOfWeek.MONDAY, saturday));
        assertEquals(saturday, WeekPages.nearestPage(DayOfWeek.SATURDAY, saturday));
    }

    @Test
    public void separateEventsEachGetTheFullWidth() {
        Event morning = Event.fixed("Lecture", "", DayOfWeek.MONDAY, 9 * 60, 10 * 60);
        Event noon = Event.fixed("Lunch", "", DayOfWeek.MONDAY, 10 * 60, 11 * 60);
        for (DayLayout.Placement placement : DayLayout.place(List.of(morning, noon))) {
            assertEquals(0, placement.column);
            assertEquals(1, placement.getColumnCount());
        }
    }

    @Test
    public void overlappingEventsSitSideBySide() {
        Event lecture = Event.fixed("Lecture", "", DayOfWeek.MONDAY, 9 * 60, 11 * 60);
        Event meeting = Event.fixed("Meeting", "", DayOfWeek.MONDAY, 10 * 60, 12 * 60);
        Event evening = Event.fixed("Practice", "", DayOfWeek.MONDAY, 18 * 60, 19 * 60);

        List<DayLayout.Placement> placements = DayLayout.place(List.of(meeting, evening, lecture));

        assertEquals("Lecture", placements.get(0).event.getName());
        assertEquals(0, placements.get(0).column);
        assertEquals(2, placements.get(0).getColumnCount());
        assertEquals("Meeting", placements.get(1).event.getName());
        assertEquals(1, placements.get(1).column);
        assertEquals(2, placements.get(1).getColumnCount());
        assertEquals("Practice", placements.get(2).event.getName());
        assertEquals(1, placements.get(2).getColumnCount());
    }
}
