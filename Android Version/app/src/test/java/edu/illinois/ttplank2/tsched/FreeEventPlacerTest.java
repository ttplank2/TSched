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
import java.util.Set;

import org.junit.Test;

public class FreeEventPlacerTest {
    private static final int EIGHT_AM = 8 * 60;
    private static final int TEN_PM = 22 * 60;

    /** Busy every day 8 AM to 6 PM, leaving 6 PM to 10 PM open. */
    private static List<Event> workWeek() {
        List<Event> week = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            week.add(Event.fixed("Work", "", day, EIGHT_AM, 18 * 60));
        }
        return week;
    }

    @Test
    public void placedEventsAvoidFixedEventsAndStayInTheWindow() {
        List<Event> week = workWeek();
        week.add(Event.free("Gym", "", 90));

        FreeEventPlacer.Suggestion suggestion =
                FreeEventPlacer.suggest(week, EIGHT_AM, TEN_PM, new Random(1));

        assertEquals(1, suggestion.placed.size());
        Event gym = suggestion.placed.get(0);
        assertTrue(gym.isPlaced());
        assertTrue(gym.getStartMinute() >= 18 * 60);
        assertTrue(gym.getEndMinute() <= TEN_PM);
    }

    @Test
    public void placedEventsRespectTheirDayRange() {
        for (int seed = 0; seed < 20; seed++) {
            List<Event> week = new ArrayList<>();
            week.add(Event.free("Gym", "", 90,
                    EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)));

            Event gym = FreeEventPlacer.suggest(week, EIGHT_AM, TEN_PM, new Random(seed)).placed.get(0);

            assertTrue(gym.getDay().getValue() <= DayOfWeek.WEDNESDAY.getValue());
        }
    }

    @Test
    public void placedEventsNeverOverlapEachOther() {
        List<Event> week = workWeek();
        for (int i = 0; i < 10; i++) {
            week.add(Event.free("Task " + i, "", 60));
        }

        FreeEventPlacer.Suggestion suggestion =
                FreeEventPlacer.suggest(week, EIGHT_AM, TEN_PM, new Random(3));

        assertEquals(10, suggestion.placed.size());
        for (Event a : suggestion.placed) {
            for (Event b : suggestion.placed) {
                if (a != b && a.getDay() == b.getDay()) {
                    assertTrue(a.getEndMinute() <= b.getStartMinute() || b.getEndMinute() <= a.getStartMinute());
                }
            }
        }
    }

    @Test
    public void eventsSpreadAcrossTheWeekInsteadOfPilingUp() {
        List<Event> week = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            week.add(Event.free("Task " + i, "", 60));
        }

        List<Event> placed = FreeEventPlacer.suggest(week, EIGHT_AM, TEN_PM, new Random(5)).placed;

        assertEquals(7, placed.stream().map(Event::getDay).distinct().count());
    }

    @Test
    public void eventsThatCannotFitAreReported() {
        List<Event> week = workWeek();
        week.add(Event.free("Long hike", "", 5 * 60));

        FreeEventPlacer.Suggestion suggestion =
                FreeEventPlacer.suggest(week, EIGHT_AM, TEN_PM, new Random(2));

        assertTrue(suggestion.placed.isEmpty());
        assertEquals("Long hike", suggestion.unfitted.get(0).getName());
    }

    @Test
    public void alreadyPlacedEventsStayPutAndCountAsBusy() {
        // "Read" already fills Monday's whole window, so a Monday-only Gym has nowhere to go.
        Event read = Event.free("Read", "", 14 * 60).placedAt(DayOfWeek.MONDAY, EIGHT_AM);
        List<Event> week = new ArrayList<>(List.of(read));
        week.add(Event.free("Gym", "", 60, EnumSet.of(DayOfWeek.MONDAY)));

        FreeEventPlacer.Suggestion suggestion =
                FreeEventPlacer.suggest(week, EIGHT_AM, TEN_PM, new Random(4));

        assertTrue(suggestion.placed.isEmpty());
        assertEquals(List.of("Gym"), suggestion.unfitted.stream().map(Event::getName).toList());
    }

    @Test
    public void windowMustEndAfterItStarts() {
        assertThrows(IllegalArgumentException.class,
                () -> FreeEventPlacer.suggest(new ArrayList<>(), TEN_PM, EIGHT_AM, new Random()));
    }

    @Test
    public void placementSurvivesSavingAndLoading() {
        Event gym = Event.free("Gym", "", 90).placedAt(DayOfWeek.FRIDAY, 18 * 60);

        Event loaded = EventCodec.decode(EventCodec.encode(List.of(gym))).get(0);

        assertTrue(loaded.isPlaced());
        assertEquals(DayOfWeek.FRIDAY, loaded.getDay());
        assertEquals(18 * 60 + 90, loaded.getEndMinute());
    }

    @Test
    public void editedFreeEventKeepsItsSpot() {
        Event placed = Event.free("Gym", "", 90).placedAt(DayOfWeek.FRIDAY, 18 * 60);
        Event renamed = Event.freeWithId(placed.getId(), "Gym and sauna", "", 120, Set.of());

        Event kept = renamed.keepPlacementOf(placed);

        assertTrue(kept.isPlaced());
        assertEquals(DayOfWeek.FRIDAY, kept.getDay());
        assertEquals(18 * 60, kept.getStartMinute());
        assertEquals("Gym and sauna", kept.getName());
    }

    @Test
    public void editedFreeEventIsUnplacedWhenItsSpotNoLongerWorks() {
        Event placed = Event.free("Gym", "", 60).placedAt(DayOfWeek.FRIDAY, 22 * 60);

        Event pastMidnight = Event.freeWithId(placed.getId(), "Gym", "", 180, Set.of());
        Event wrongDays = Event.freeWithId(placed.getId(), "Gym", "", 60,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));

        assertFalse(pastMidnight.keepPlacementOf(placed).isPlaced());
        assertFalse(wrongDays.keepPlacementOf(placed).isPlaced());
    }

    @Test
    public void recalledEventGoesBackToUnplacedKeepingItsDetails() {
        int blue = EventColors.COLORS[7];
        Event placed = Event.free("Gym", "Arc", 90,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
                .withColor(blue).placedAt(DayOfWeek.TUESDAY, 18 * 60);

        Event recalled = EventCodec.decode(EventCodec.encode(List.of(placed.unplaced()))).get(0);

        assertFalse(recalled.isPlaced());
        assertEquals(placed.getId(), recalled.getId());
        assertEquals("Gym", recalled.getName());
        assertEquals(90, recalled.getDurationMinutes());
        assertEquals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), recalled.getAllowedDays());
        assertEquals(blue, recalled.getColor());
    }

    @Test
    public void recallAllUnplacesEveryPlacedEventAndLeavesFixedOnesAlone() {
        Event lecture = Event.fixed("Lecture", "", DayOfWeek.MONDAY, 9 * 60, 10 * 60);
        Event gym = Event.free("Gym", "", 60).placedAt(DayOfWeek.TUESDAY, 18 * 60);
        Event read = Event.free("Read", "", 30).placedAt(DayOfWeek.FRIDAY, 20 * 60);
        Event laundry = Event.free("Laundry", "", 60);
        List<Event> week = List.of(lecture, gym, read, laundry);

        List<Event> recalled = FreeEventPlacer.recallAll(week);

        assertEquals(2, FreeEventPlacer.countPlaced(week));
        assertEquals(0, FreeEventPlacer.countPlaced(recalled));
        assertEquals(4, recalled.size());
        assertEquals(DayOfWeek.MONDAY, recalled.get(0).getDay());
        assertEquals(gym.getId(), recalled.get(1).getId());
    }
}