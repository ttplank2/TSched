package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

public class EventTest {
    @Test
    public void fixedEventKeepsItsTimes() {
        Event lecture = Event.fixed(" CS 124 ", "Foellinger", DayOfWeek.TUESDAY, 9 * 60, 10 * 60 + 15);
        assertEquals("CS 124", lecture.getName());
        assertEquals(DayOfWeek.TUESDAY, lecture.getDay());
        assertEquals(75, lecture.getDurationMinutes());
        assertEquals(10 * 60 + 15, lecture.getEndMinute());
    }

    @Test
    public void fixedEventRejectsBadInput() {
        assertThrows(IllegalArgumentException.class,
                () -> Event.fixed("  ", "", DayOfWeek.MONDAY, 600, 660));
        assertThrows(IllegalArgumentException.class,
                () -> Event.fixed("Work", "", DayOfWeek.MONDAY, 660, 600));
        assertThrows(IllegalArgumentException.class,
                () -> Event.fixed("Work", "", null, 600, 660));
    }

    @Test
    public void freeEventNeedsANameAndALength() {
        Event study = Event.free(" Study ", null, 120);
        assertEquals("Study", study.getName());
        assertEquals("", study.getLocation());
        assertNull(study.getDay());
        assertFalse(study.hasDayLimit());
        assertThrows(IllegalArgumentException.class, () -> Event.free("  ", "", 60));
        assertThrows(IllegalArgumentException.class, () -> Event.free("Gym", "", 0));
    }

    @Test
    public void freeEventCanBeLimitedToAnySetOfDays() {
        Event weekend = Event.free("Hike", "", 180,
                EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.MONDAY));
        assertTrue(weekend.hasDayLimit());
        assertTrue(weekend.allowsDay(DayOfWeek.SATURDAY));
        assertTrue(weekend.allowsDay(DayOfWeek.MONDAY));
        assertFalse(weekend.allowsDay(DayOfWeek.WEDNESDAY));
        Event anyDay = Event.free("Laundry", "", 60);
        assertTrue(anyDay.allowsDay(DayOfWeek.WEDNESDAY));
    }

    @Test
    public void choosingNoDaysOrEveryDayMeansAnyDay() {
        Event noDays = Event.free("Gym", "", 60, EnumSet.noneOf(DayOfWeek.class));
        Event everyDay = Event.free("Gym", "", 60, EnumSet.allOf(DayOfWeek.class));
        Event sixDays = Event.free("Gym", "", 60, EnumSet.complementOf(EnumSet.of(DayOfWeek.SUNDAY)));

        assertFalse(noDays.hasDayLimit());
        assertFalse(everyDay.hasDayLimit());
        assertTrue(everyDay.getAllowedDays().isEmpty());
        assertTrue(sixDays.hasDayLimit());
        assertFalse(sixDays.allowsDay(DayOfWeek.SUNDAY));
    }

    @Test
    public void savedEventsLoadBackTheSame() {
        Event lecture = Event.fixed("CS 124\tlecture", "Room \\ 1", DayOfWeek.TUESDAY, 540, 615);
        Event study = Event.free("Study", "Library\nfloor 2", 120,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY));

        List<Event> loaded = EventCodec.decode(EventCodec.encode(List.of(lecture, study)));

        assertEquals(2, loaded.size());
        Event loadedLecture = loaded.get(0);
        assertEquals(lecture.getId(), loadedLecture.getId());
        assertEquals("CS 124\tlecture", loadedLecture.getName());
        assertEquals("Room \\ 1", loadedLecture.getLocation());
        assertEquals(DayOfWeek.TUESDAY, loadedLecture.getDay());
        assertEquals(615, loadedLecture.getEndMinute());
        Event loadedStudy = loaded.get(1);
        assertEquals(Event.Kind.FREE, loadedStudy.getKind());
        assertEquals("Library\nfloor 2", loadedStudy.getLocation());
        assertEquals(120, loadedStudy.getDurationMinutes());
        assertEquals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
                loadedStudy.getAllowedDays());
    }

    @Test
    public void freeEventsSavedByTheOldVersionStillLoad() {
        // Before day ranges, free events stored a time window, and names were optional.
        String oldFile = "id1\tFREE\t\t\t-\t-1\t90\t480\t1200\n"
                + "id2\tFREE\tGym\t\t-\t-1\t60\t-1\t-1\n";

        List<Event> loaded = EventCodec.decode(oldFile);

        assertEquals(2, loaded.size());
        assertEquals("Free time", loaded.get(0).getName());
        assertFalse(loaded.get(0).hasDayLimit());
        assertEquals(90, loaded.get(0).getDurationMinutes());
        assertEquals("Gym", loaded.get(1).getName());
    }

    @Test
    public void freeEventsSavedWithADayRangeLoadAsTheSameDays() {
        // The old format stored the first and last day of a range, which could wrap past Sunday.
        String oldFile = "id1\tFREE\tHike\t\t-\t-1\t180\tFRIDAY\tMONDAY\tffffc93c\n"
                + "id2\tFREE\tRead\t\t-\t-1\t60\tTUESDAY\tTUESDAY\tffffc93c\n";

        List<Event> loaded = EventCodec.decode(oldFile);

        assertEquals(EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY),
                loaded.get(0).getAllowedDays());
        assertEquals(EnumSet.of(DayOfWeek.TUESDAY), loaded.get(1).getAllowedDays());
    }

    @Test
    public void loadingSkipsDamagedLinesAndHandlesNoData() {
        assertTrue(EventCodec.decode("").isEmpty());
        String good = EventCodec.encode(List.of(Event.free("Gym", "", 60)));
        assertEquals(1, EventCodec.decode("garbage\n" + good).size());
    }

    @Test
    public void timesFormatForDisplay() {
        assertEquals("12:00 AM", TimeFormat.clock(0));
        assertEquals("2:30 PM", TimeFormat.clock(14 * 60 + 30));
        assertEquals("1h 30m", TimeFormat.length(90));
        assertEquals("45m", TimeFormat.length(45));
    }

    @Test
    public void eventColorSurvivesSavingAndLoading() {
        int red = EventColors.COLORS[0];
        Event lecture = Event.fixed("Lecture", "", DayOfWeek.MONDAY, 540, 600).withColor(red);
        Event gym = Event.free("Gym", "", 60).withColor(red).placedAt(DayOfWeek.FRIDAY, 600);

        List<Event> loaded = EventCodec.decode(EventCodec.encode(List.of(lecture, gym)));

        assertEquals(red, loaded.get(0).getColor());
        assertEquals("placing keeps the color", red, loaded.get(1).getColor());
    }

    @Test
    public void eventsSavedBeforeColorsLoadAsDefaultYellow() {
        String oldFile = "id1\tFIXED\tWork\t\tMONDAY\t540\t60\t-\t-\n";

        Event loaded = EventCodec.decode(oldFile).get(0);

        assertEquals(EventColors.DEFAULT, loaded.getColor());
    }

    @Test
    public void colorWheelHasTenNamedColorsIncludingTheDefault() {
        assertTrue(Arrays.stream(EventColors.COLORS).anyMatch(c -> c == EventColors.DEFAULT));
        assertEquals("Red", EventColors.nameOf(EventColors.COLORS[0]));
        assertEquals(10, EventColors.COLORS.length);
        assertEquals(EventColors.COLORS.length, EventColors.NAMES.length);
        for (String removed : new String[] {"Brown", "White", "Slate", "Cyan", "Indigo"}) {
            assertFalse(Arrays.asList(EventColors.NAMES).contains(removed));
        }
    }

    @Test
    public void eventsInRemovedColorsLoadAsTheirReplacements() {
        // Cyan became teal, indigo became purple, and brown, white, and slate became yellow.
        String file = "id1\tFIXED\tA\t\tMONDAY\t540\t60\t-\t-\tff4dd0e1\n"
                + "id2\tFIXED\tB\t\tMONDAY\t660\t60\t-\t-\tff5c6bc0\n"
                + "id3\tFIXED\tC\t\tMONDAY\t780\t60\t-\t-\tffa1887f\n"
                + "id4\tFIXED\tD\t\tMONDAY\t900\t60\t-\t-\tffe0e0e0\n"
                + "id5\tFIXED\tE\t\tMONDAY\t1000\t60\t-\t-\tff90a4ae\n"
                + "id6\tFIXED\tF\t\tMONDAY\t1100\t60\t-\t-\tff42a5f5\n";

        List<Event> loaded = EventCodec.decode(file);

        assertEquals("Teal", EventColors.nameOf(loaded.get(0).getColor()));
        assertEquals("Purple", EventColors.nameOf(loaded.get(1).getColor()));
        assertEquals(EventColors.DEFAULT, loaded.get(2).getColor());
        assertEquals(EventColors.DEFAULT, loaded.get(3).getColor());
        assertEquals(EventColors.DEFAULT, loaded.get(4).getColor());
        assertEquals("a color still on the wheel is kept", "Blue",
                EventColors.nameOf(loaded.get(5).getColor()));
    }
}
