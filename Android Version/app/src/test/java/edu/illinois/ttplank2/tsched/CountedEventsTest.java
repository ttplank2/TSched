package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.Test;

public class CountedEventsTest {
    /** A class on Tuesdays, 9:00 to 10:00 AM, with the given number of counts. */
    private static Event tuesdayClass(int count) {
        return Event.countedWithId("class", "Class", "", DayOfWeek.TUESDAY, 9 * 60, 60, count);
    }

    /** A time in October 2026, when the 13th is a Tuesday. */
    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 10, day, hour, minute);
    }

    @Test
    public void nothingHappensBeforeTheEndTime() {
        List<Event> events = List.of(tuesdayClass(5));

        List<Event> after = CountedEvents.advance(events, at(13, 8, 0), at(13, 9, 59));

        assertSame(events.get(0), after.get(0));
    }

    @Test
    public void passingTheEndTimeLosesOneCount() {
        List<Event> after = CountedEvents.advance(List.of(tuesdayClass(5)), at(13, 8, 0), at(13, 10, 1));

        assertEquals(4, after.get(0).getCount());
    }

    @Test
    public void theEndTimeItselfCounts() {
        List<Event> after = CountedEvents.advance(List.of(tuesdayClass(5)), at(13, 8, 0), at(13, 10, 0));

        assertEquals(4, after.get(0).getCount());
    }

    @Test
    public void anEndTimeIsNeverCountedTwice() {
        Event event = tuesdayClass(5);
        List<Event> first = CountedEvents.advance(List.of(event), at(13, 8, 0), at(13, 11, 0));

        List<Event> second = CountedEvents.advance(first, at(13, 11, 0), at(13, 12, 0));

        assertEquals(4, second.get(0).getCount());
    }

    @Test
    public void aWeekAwayCostsOneCountAndTwoWeeksCostTwo() {
        // Opened Tuesday noon: that day's 9-10 AM class had already ended.
        List<Event> events = List.of(tuesdayClass(10));

        assertEquals(9, CountedEvents.advance(events, at(13, 12, 0), at(20, 12, 0)).get(0).getCount());
        assertEquals(8, CountedEvents.advance(events, at(13, 12, 0), at(27, 12, 0)).get(0).getCount());
    }

    @Test
    public void everyEventLosesTheSameForTheSameTimeAway() {
        Event monday = Event.countedWithId("m", "Mon", "", DayOfWeek.MONDAY, 600, 60, 9);
        Event friday = Event.countedWithId("f", "Fri", "", DayOfWeek.FRIDAY, 600, 60, 9);

        List<Event> after = CountedEvents.advance(List.of(monday, friday), at(13, 12, 0), at(27, 12, 0));

        assertEquals(7, after.get(0).getCount());
        assertEquals(7, after.get(1).getCount());
    }

    @Test
    public void aCountThatReachesZeroDeletesTheEvent() {
        Event gone = tuesdayClass(2);
        Event kept = Event.countedWithId("kept", "Lab", "", DayOfWeek.TUESDAY, 9 * 60, 60, 3);
        Event other = Event.fixed("Gym", "", DayOfWeek.TUESDAY, 540, 600);

        List<Event> after = CountedEvents.advance(List.of(gone, kept, other), at(13, 12, 0), at(27, 12, 0));

        assertEquals(2, after.size());
        assertEquals(1, after.get(0).getCount());
        assertSame("fixed events are untouched", other, after.get(1));
    }

    @Test
    public void aLongTimeAwayJustDeletesTheEvent() {
        List<Event> after = CountedEvents.advance(List.of(tuesdayClass(52)), at(13, 12, 0),
                LocalDateTime.of(2030, 10, 1, 0, 0));

        assertTrue(after.isEmpty());
    }

    @Test
    public void anOvernightEventEndsTheNextMorning() {
        // Sleep Tuesday 11 PM to 7 AM ends Wednesday at 7 AM.
        Event sleep = Event.countedWithId("s", "Sleep", "", DayOfWeek.TUESDAY, 23 * 60, 8 * 60, 5);

        assertEquals(5, CountedEvents.advance(List.of(sleep), at(13, 12, 0), at(14, 6, 59)).get(0).getCount());
        assertEquals(4, CountedEvents.advance(List.of(sleep), at(13, 12, 0), at(14, 7, 0)).get(0).getCount());
    }

    @Test
    public void aSundayNightEventEndsOnMonday() {
        // October 18 is a Sunday; this event ends Monday the 19th at 7 AM.
        Event sleep = Event.countedWithId("s", "Sleep", "", DayOfWeek.SUNDAY, 23 * 60, 8 * 60, 5);

        assertEquals(5, CountedEvents.advance(List.of(sleep), at(18, 12, 0), at(19, 6, 0)).get(0).getCount());
        assertEquals(4, CountedEvents.advance(List.of(sleep), at(18, 12, 0), at(19, 8, 0)).get(0).getCount());
        // Opened Monday at 8 AM, just after it ended: the next end is a week later.
        assertEquals(5, CountedEvents.advance(List.of(sleep), at(19, 8, 0), at(20, 8, 0)).get(0).getCount());
    }

    @Test
    public void theNextEndIsTheEarliestEndTimeStillToCome() {
        Event monday = Event.countedWithId("m", "Mon", "", DayOfWeek.MONDAY, 600, 60, 3);
        Event friday = Event.countedWithId("f", "Fri", "", DayOfWeek.FRIDAY, 600, 60, 3);
        Event fixed = Event.fixed("Gym", "", DayOfWeek.TUESDAY, 540, 600);

        // Tuesday the 13th: Friday the 16th at 11 AM is next, and fixed events never count.
        assertEquals(at(16, 11, 0), CountedEvents.nextEnd(List.of(monday, friday, fixed), at(13, 12, 0)));
        // After Friday has ended, Monday the 19th at 11 AM is next.
        assertEquals(at(19, 11, 0), CountedEvents.nextEnd(List.of(monday, friday), at(16, 11, 0)));
    }

    @Test
    public void theNextEndHandlesOvernightEventsAndHasNoneWithoutCountedEvents() {
        Event sleep = Event.countedWithId("s", "Sleep", "", DayOfWeek.SUNDAY, 23 * 60, 8 * 60, 3);

        // Sunday the 18th at noon: the sleep that began that night ends Monday at 7 AM.
        assertEquals(at(19, 7, 0), CountedEvents.nextEnd(List.of(sleep), at(18, 12, 0)));
        assertEquals(null, CountedEvents.nextEnd(List.of(Event.fixed("Gym", "", DayOfWeek.TUESDAY, 540, 600)),
                at(13, 12, 0)));
    }

    @Test
    public void aClockSetBackChangesNothing() {
        List<Event> events = List.of(tuesdayClass(5));

        assertSame(events.get(0), CountedEvents.advance(events, at(20, 12, 0), at(13, 12, 0)).get(0));
    }

    @Test
    public void aCountMustBeFromOneToFiftyTwo() {
        assertThrows(IllegalArgumentException.class, () ->
                Event.countedWithId("x", "A", "", DayOfWeek.MONDAY, 540, 60, 0));
        assertThrows(IllegalArgumentException.class, () ->
                Event.countedWithId("x", "A", "", DayOfWeek.MONDAY, 540, 60, 53));
        assertEquals(52, Event.countedWithId("x", "A", "", DayOfWeek.MONDAY, 540, 60, 52).getCount());
    }

    @Test
    public void countedEventsSurviveSavingAndLoadingAndOldFilesStillLoad() {
        Event counted = tuesdayClass(7).withColor(EventColors.COLORS[3]);

        Event loaded = EventCodec.decode(EventCodec.encode(List.of(counted))).get(0);

        assertEquals(Event.Kind.COUNTED, loaded.getKind());
        assertEquals(7, loaded.getCount());
        assertEquals(DayOfWeek.TUESDAY, loaded.getDay());
        assertEquals(EventColors.COLORS[3], loaded.getColor());
        String oldFile = "id1\tFIXED\tClass\t\tMONDAY\t540\t60\t-\t-\tffffc93c\t\n";
        assertEquals(0, EventCodec.decode(oldFile).get(0).getCount());
    }
}
