package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class OvernightEventTest {
    private static final int ELEVEN_PM = 23 * 60;
    private static final int EIGHT_HOURS = 8 * 60;

    private static Event sleepOn(DayOfWeek day) {
        return Event.fixedWithId("sleep-" + day, "Sleep", "", day, ELEVEN_PM, EIGHT_HOURS);
    }

    @Test
    public void anEventCanRunPastMidnight() {
        Event sleep = sleepOn(DayOfWeek.MONDAY);

        assertTrue(sleep.crossesMidnight());
        assertEquals(DayOfWeek.MONDAY, sleep.getDay());
        assertEquals(ELEVEN_PM + EIGHT_HOURS, sleep.getEndMinute());
        assertFalse(Event.fixed("Class", "", DayOfWeek.MONDAY, 540, 600).crossesMidnight());
    }

    @Test
    public void anEventCanBeAtMostADayLong() {
        Event wholeDay = Event.fixedWithId("id", "Trip", "", DayOfWeek.MONDAY, 600, 24 * 60);
        assertTrue(wholeDay.crossesMidnight());
        assertThrows(IllegalArgumentException.class,
                () -> Event.fixedWithId("id", "Trip", "", DayOfWeek.MONDAY, 600, 24 * 60 + 5));
    }

    @Test
    public void anOvernightEventShowsOnBothDays() {
        List<Event> week = List.of(sleepOn(DayOfWeek.MONDAY));

        List<DaySegment> monday = DaySegment.forDay(week, DayOfWeek.MONDAY);
        List<DaySegment> tuesday = DaySegment.forDay(week, DayOfWeek.TUESDAY);

        assertEquals(1, monday.size());
        assertEquals(ELEVEN_PM, monday.get(0).startMinute);
        assertEquals(24 * 60, monday.get(0).endMinute);
        assertEquals(1, tuesday.size());
        assertEquals(0, tuesday.get(0).startMinute);
        assertEquals(7 * 60, tuesday.get(0).endMinute);
        assertEquals("both parts are the same event", monday.get(0).event, tuesday.get(0).event);
        assertTrue(DaySegment.forDay(week, DayOfWeek.WEDNESDAY).isEmpty());
        assertTrue(DaySegment.forDay(week, DayOfWeek.SUNDAY).isEmpty());
    }

    @Test
    public void theEveningPartPointsDownAndTheMorningPartPointsUp() {
        List<Event> week = List.of(sleepOn(DayOfWeek.MONDAY), Event.fixed("Class", "", DayOfWeek.MONDAY, 540, 600));

        List<DaySegment> monday = DaySegment.forDay(week, DayOfWeek.MONDAY);
        DaySegment tuesdayMorning = DaySegment.forDay(week, DayOfWeek.TUESDAY).get(0);

        assertTrue(monday.get(0).continuesAfter);
        assertFalse(monday.get(0).continuesFrom);
        assertFalse("a normal event has no triangle", monday.get(1).continuesAfter);
        assertTrue(tuesdayMorning.continuesFrom);
        assertFalse(tuesdayMorning.continuesAfter);
    }

    @Test
    public void anOvernightEventOnSundayContinuesOntoMonday() {
        List<Event> week = List.of(sleepOn(DayOfWeek.SUNDAY));

        assertEquals(1, DaySegment.forDay(week, DayOfWeek.MONDAY).size());
        assertEquals(7 * 60, DaySegment.forDay(week, DayOfWeek.MONDAY).get(0).endMinute);
    }

    @Test
    public void theMorningPartSitsBesideAnEventAtThatTime() {
        Event breakfast = Event.fixed("Breakfast", "", DayOfWeek.TUESDAY, 6 * 60, 7 * 60);
        List<Event> week = List.of(sleepOn(DayOfWeek.MONDAY), breakfast);

        List<DayLayout.Placement> tuesday = DayLayout.placeSegments(
                DaySegment.forDay(week, DayOfWeek.TUESDAY));

        assertEquals(2, tuesday.size());
        assertEquals(2, tuesday.get(0).getColumnCount());
        assertEquals("Sleep", tuesday.get(0).event.getName());
        assertEquals(7 * 60, tuesday.get(0).endMinute);
    }

    @Test
    public void overnightEventsSurviveSavingAndLoading() {
        Event sleep = sleepOn(DayOfWeek.FRIDAY);

        Event loaded = EventCodec.decode(EventCodec.encode(List.of(sleep))).get(0);

        assertTrue(loaded.crossesMidnight());
        assertEquals(ELEVEN_PM, loaded.getStartMinute());
        assertEquals(EIGHT_HOURS, loaded.getDurationMinutes());
    }

    @Test
    public void placedFreeEventsAvoidTheMorningAfterAnOvernightEvent() {
        // Sleep ends Tuesday at 7 AM, so a free event limited to Tuesday starts after that.
        List<Event> week = new ArrayList<>();
        week.add(sleepOn(DayOfWeek.MONDAY));
        week.add(Event.free("Run", "", 60, EnumSet.of(DayOfWeek.TUESDAY)));

        for (int seed = 0; seed < 20; seed++) {
            FreeEventPlacer.Suggestion suggestion =
                    FreeEventPlacer.suggest(week, 0, 12 * 60, new Random(seed));

            assertEquals(1, suggestion.placed.size());
            assertTrue(suggestion.placed.get(0).getStartMinute() >= 7 * 60);
        }
    }
}
