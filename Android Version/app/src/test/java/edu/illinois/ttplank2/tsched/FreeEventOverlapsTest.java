package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.util.List;

import org.junit.Test;

public class FreeEventOverlapsTest {
    private static Event placedStudy(DayOfWeek day, int startMinute) {
        return Event.free("Study", "", 120).placedAt(day, startMinute);
    }

    @Test
    public void aFixedEventOnAPlacedFreeEventsTimeIsAnOverlap() {
        Event study = placedStudy(DayOfWeek.TUESDAY, 15 * 60);
        Event meeting = Event.fixed("Meeting", "", DayOfWeek.TUESDAY, 16 * 60, 17 * 60);

        List<FreeEventOverlaps.Overlap> overlaps =
                FreeEventOverlaps.find(List.of(study, meeting), List.of(meeting));

        assertEquals(1, overlaps.size());
        assertEquals("Study", overlaps.get(0).free.getName());
        assertEquals("Meeting", overlaps.get(0).fixed.getName());
    }

    @Test
    public void touchingEndToEndIsNotAnOverlap() {
        Event study = placedStudy(DayOfWeek.TUESDAY, 15 * 60);
        Event before = Event.fixed("Lunch", "", DayOfWeek.TUESDAY, 14 * 60, 15 * 60);
        Event after = Event.fixed("Meeting", "", DayOfWeek.TUESDAY, 17 * 60, 18 * 60);

        assertTrue(FreeEventOverlaps.find(List.of(study, before, after), List.of(before, after)).isEmpty());
    }

    @Test
    public void otherDaysAndUnplacedFreeEventsAreNotOverlaps() {
        Event study = placedStudy(DayOfWeek.TUESDAY, 15 * 60);
        Event unplaced = Event.free("Read", "", 60);
        Event wednesday = Event.fixed("Meeting", "", DayOfWeek.WEDNESDAY, 15 * 60, 17 * 60);

        assertTrue(FreeEventOverlaps.find(List.of(study, unplaced, wednesday), List.of(wednesday)).isEmpty());
    }

    @Test
    public void onlyTheJustSavedEventsAreChecked() {
        Event study = placedStudy(DayOfWeek.TUESDAY, 15 * 60);
        Event oldOverlap = Event.fixed("Old", "", DayOfWeek.TUESDAY, 15 * 60, 16 * 60);
        Event newer = Event.fixed("Newer", "", DayOfWeek.THURSDAY, 15 * 60, 16 * 60);

        assertTrue(FreeEventOverlaps.find(List.of(study, oldOverlap, newer), List.of(newer)).isEmpty());
    }

    @Test
    public void aCountedEventAndAnOvernightEventsMorningOverlapToo() {
        Event study = placedStudy(DayOfWeek.WEDNESDAY, 6 * 60);
        Event sleep = Event.fixedWithId("s", "Sleep", "", DayOfWeek.TUESDAY, 23 * 60, 8 * 60);
        Event counted = Event.countedWithId("c", "Class", "", DayOfWeek.WEDNESDAY, 7 * 60, 120, 5);

        assertEquals(1, FreeEventOverlaps.find(List.of(study), List.of(sleep)).size());
        assertEquals(1, FreeEventOverlaps.find(List.of(study), List.of(counted)).size());
    }

    @Test
    public void theNoticeIsTakenOnlyOnce() {
        Event study = placedStudy(DayOfWeek.TUESDAY, 15 * 60);
        Event meeting = Event.fixed("Meeting", "", DayOfWeek.TUESDAY, 16 * 60, 17 * 60);
        OverlapNotice.leave(FreeEventOverlaps.find(List.of(study), List.of(meeting)));

        assertEquals(1, OverlapNotice.take().size());
        assertTrue(OverlapNotice.take().isEmpty());
    }
}
