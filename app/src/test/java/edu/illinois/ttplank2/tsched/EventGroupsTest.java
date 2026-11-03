package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class EventGroupsTest {
    private static Event lecture(String name) {
        return Event.fixed(name, "Siebel", DayOfWeek.MONDAY, 9 * 60, 10 * 60)
                .withColor(EventColors.COLORS[2]);
    }

    private static List<Event> eventsOn(List<Event> events, DayOfWeek day) {
        List<Event> found = new ArrayList<>();
        for (Event event : events) {
            if (event.getDay() == day) {
                found.add(event);
            }
        }
        return found;
    }

    @Test
    public void severalDaysMakeOneGroupWithTheSameDetails() {
        List<Event> saved = EventGroups.saveFixed(new ArrayList<>(), lecture("Class"),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));

        assertEquals(3, saved.size());
        Set<String> groups = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (Event event : saved) {
            assertEquals("Class", event.getName());
            assertEquals("Siebel", event.getLocation());
            assertEquals(9 * 60, event.getStartMinute());
            assertEquals(60, event.getDurationMinutes());
            assertEquals(EventColors.COLORS[2], event.getColor());
            assertTrue(event.isGrouped());
            groups.add(event.getGroupId());
            ids.add(event.getId());
        }
        assertEquals("one shared group", 1, groups.size());
        assertEquals("each event has its own id", 3, ids.size());
    }

    @Test
    public void oneDayStaysUngrouped() {
        List<Event> saved = EventGroups.saveFixed(new ArrayList<>(), lecture("Class"),
                EnumSet.of(DayOfWeek.TUESDAY));

        assertEquals(1, saved.size());
        assertEquals(DayOfWeek.TUESDAY, saved.get(0).getDay());
        assertFalse(saved.get(0).isGrouped());
    }

    @Test
    public void editingOneMemberUpdatesTheWholeGroup() {
        List<Event> saved = EventGroups.saveFixed(new ArrayList<>(), lecture("Class"),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        Event wednesday = eventsOn(saved, DayOfWeek.WEDNESDAY).get(0);
        Event mondayBefore = eventsOn(saved, DayOfWeek.MONDAY).get(0);

        Event edited = Event.fixedWithId(wednesday.getId(), "Lab", "Grainger", DayOfWeek.WEDNESDAY,
                13 * 60, 90).withColor(EventColors.COLORS[4]);
        List<Event> after = EventGroups.saveFixed(saved, edited,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));

        assertEquals(2, after.size());
        for (Event event : after) {
            assertEquals("Lab", event.getName());
            assertEquals("Grainger", event.getLocation());
            assertEquals(13 * 60, event.getStartMinute());
            assertEquals(90, event.getDurationMinutes());
            assertEquals(EventColors.COLORS[4], event.getColor());
            assertEquals(mondayBefore.getGroupId(), event.getGroupId());
        }
        assertEquals("days keep their events", mondayBefore.getId(),
                eventsOn(after, DayOfWeek.MONDAY).get(0).getId());
    }

    @Test
    public void editingCanAddAndRemoveDays() {
        List<Event> saved = EventGroups.saveFixed(new ArrayList<>(), lecture("Class"),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        Event monday = eventsOn(saved, DayOfWeek.MONDAY).get(0);
        String group = monday.getGroupId();

        List<Event> after = EventGroups.saveFixed(saved, monday,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY));

        assertEquals(2, after.size());
        assertEquals(1, eventsOn(after, DayOfWeek.MONDAY).size());
        assertEquals(1, eventsOn(after, DayOfWeek.THURSDAY).size());
        assertTrue(eventsOn(after, DayOfWeek.WEDNESDAY).isEmpty());
        assertEquals(group, eventsOn(after, DayOfWeek.THURSDAY).get(0).getGroupId());
    }

    @Test
    public void aLoneEventCanBeSpreadOverMoreDays() {
        List<Event> saved = EventGroups.saveFixed(new ArrayList<>(), lecture("Class"),
                EnumSet.of(DayOfWeek.MONDAY));
        Event monday = saved.get(0);

        List<Event> after = EventGroups.saveFixed(saved, monday,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));

        assertEquals(2, after.size());
        assertEquals(after.get(0).getGroupId(), after.get(1).getGroupId());
        assertTrue(after.get(0).isGrouped());
    }

    @Test
    public void otherEventsAreLeftAlone() {
        Event gym = Event.fixed("Gym", "", DayOfWeek.SUNDAY, 600, 660);
        List<Event> saved = new ArrayList<>(List.of(gym));

        List<Event> after = EventGroups.saveFixed(saved, lecture("Class"),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));

        assertEquals(3, after.size());
        assertTrue(after.contains(gym));
    }

    @Test
    public void deletingOneMemberDeletesTheWholeGroup() {
        Event gym = Event.fixed("Gym", "", DayOfWeek.SUNDAY, 600, 660);
        List<Event> saved = EventGroups.saveFixed(new ArrayList<>(List.of(gym)), lecture("Class"),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        Event wednesday = eventsOn(saved, DayOfWeek.WEDNESDAY).get(0);

        List<Event> after = EventGroups.withoutGroupOf(saved, wednesday);

        assertEquals(List.of(gym), after);
    }

    @Test
    public void deletingALoneEventLeavesTheRest() {
        Event gym = Event.fixed("Gym", "", DayOfWeek.SUNDAY, 600, 660);
        Event lab = Event.fixed("Lab", "", DayOfWeek.MONDAY, 600, 660);

        List<Event> after = EventGroups.withoutGroupOf(List.of(gym, lab), lab);

        assertEquals(List.of(gym), after);
    }

    @Test
    public void editingACountedGroupKeepsEachDaysCountUnlessTheCountChanges() {
        Event details = Event.countedWithId("c", "Class", "", DayOfWeek.MONDAY, 540, 60, 10);
        List<Event> saved = EventGroups.saveFixed(new ArrayList<>(), details,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        // Monday has already ended once, so it has one fewer left than Wednesday.
        Event monday = eventsOn(saved, DayOfWeek.MONDAY).get(0);
        saved.set(saved.indexOf(monday), monday.withCount(9));
        Event edited = Event.countedWithId(monday.getId(), "Lecture", "", DayOfWeek.MONDAY, 540, 60, 9);
        EnumSet<DayOfWeek> days = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

        List<Event> kept = EventGroups.saveFixed(saved, edited, days, true);

        assertEquals(9, eventsOn(kept, DayOfWeek.MONDAY).get(0).getCount());
        assertEquals(10, eventsOn(kept, DayOfWeek.WEDNESDAY).get(0).getCount());
        assertEquals("a new day starts with the count in the form", 9,
                eventsOn(kept, DayOfWeek.FRIDAY).get(0).getCount());
        assertEquals("Lecture", eventsOn(kept, DayOfWeek.WEDNESDAY).get(0).getName());

        Event changed = Event.countedWithId(monday.getId(), "Lecture", "", DayOfWeek.MONDAY, 540, 60, 20);
        List<Event> reset = EventGroups.saveFixed(saved, changed, days, false);

        for (Event event : reset) {
            assertEquals(20, event.getCount());
        }
    }

    @Test
    public void savingNeedsAtLeastOneDay() {
        assertThrows(IllegalArgumentException.class, () ->
                EventGroups.saveFixed(new ArrayList<>(), lecture("Class"),
                        EnumSet.noneOf(DayOfWeek.class)));
    }

    @Test
    public void groupsSurviveSavingAndLoading() {
        List<Event> saved = EventGroups.saveFixed(new ArrayList<>(), lecture("Class"),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));

        List<Event> loaded = EventCodec.decode(EventCodec.encode(saved));

        assertEquals(2, loaded.size());
        assertEquals(loaded.get(0).getGroupId(), loaded.get(1).getGroupId());
        assertTrue(loaded.get(0).isGrouped());
        assertEquals(2, EventGroups.membersOf(loaded, loaded.get(0)).size());
    }

    @Test
    public void eventsSavedBeforeGroupsLoadUngrouped() {
        String oldFile = "id1\tFIXED\tClass\t\tMONDAY\t540\t60\t-\t-\tffffc93c\n";

        Event loaded = EventCodec.decode(oldFile).get(0);

        assertEquals("Class", loaded.getName());
        assertFalse(loaded.isGrouped());
    }

    private static Event study() {
        return Event.free("Study", "Library", 90).withColor(EventColors.COLORS[2]);
    }

    @Test
    public void aFreeEventOnSeveralDaysBecomesAGroupLimitedToOneDayEach() {
        List<Event> saved = EventGroups.saveFree(new ArrayList<>(), study(),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY));

        assertEquals(2, saved.size());
        assertEquals(saved.get(0).getGroupId(), saved.get(1).getGroupId());
        assertTrue(saved.get(0).isGrouped());
        for (Event member : saved) {
            assertEquals(Event.Kind.FREE, member.getKind());
            assertEquals(1, member.getAllowedDays().size());
            assertFalse(member.isPlaced());
            assertEquals(90, member.getDurationMinutes());
        }
        assertFalse(saved.get(0).getAllowedDays().equals(saved.get(1).getAllowedDays()));
    }

    @Test
    public void aFreeEventOnOneDayStandsAlone() {
        List<Event> saved = EventGroups.saveFree(new ArrayList<>(), study(), EnumSet.of(DayOfWeek.FRIDAY));

        assertEquals(1, saved.size());
        assertFalse(saved.get(0).isGrouped());
    }

    @Test
    public void aFreeEventNeedsAtLeastOneDay() {
        assertThrows(IllegalArgumentException.class,
                () -> EventGroups.saveFree(new ArrayList<>(), study(), EnumSet.noneOf(DayOfWeek.class)));
    }

    @Test
    public void editingAFreeGroupKeepsItsPlacedDaysPlacedAndDropsRemovedDays() {
        List<Event> saved = EventGroups.saveFree(new ArrayList<>(), study(),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY));
        List<Event> placed = new ArrayList<>();
        for (Event member : saved) {
            placed.add(member.getAllowedDays().contains(DayOfWeek.MONDAY)
                    ? member.placedAt(DayOfWeek.MONDAY, 10 * 60) : member);
        }
        Event edited = Event.freeWithId(saved.get(0).getId(), "Study hard", "Library", 90,
                EnumSet.noneOf(DayOfWeek.class)).withColor(EventColors.COLORS[2]);

        List<Event> result = EventGroups.saveFree(placed, edited,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY));

        assertEquals(3, result.size());
        Event monday = null;
        for (Event member : result) {
            assertEquals("Study hard", member.getName());
            if (member.getAllowedDays().contains(DayOfWeek.MONDAY)) {
                monday = member;
            }
        }
        assertTrue(monday.isPlaced());
        assertEquals(10 * 60, monday.getStartMinute());
        assertEquals(1, new HashSet<>(java.util.Arrays.asList(
                result.get(0).getGroupId(), result.get(1).getGroupId(), result.get(2).getGroupId())).size());
    }

    @Test
    public void placingAndRecallingAGroupMemberKeepsItInTheGroup() {
        List<Event> saved = EventGroups.saveFree(new ArrayList<>(), study(),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        Event member = saved.get(0);
        DayOfWeek day = member.getAllowedDays().iterator().next();

        assertEquals(member.getGroupId(), member.placedAt(day, 9 * 60).getGroupId());
        assertEquals(member.getGroupId(), member.placedAt(day, 9 * 60).unplaced().getGroupId());
    }

    @Test
    public void unplacedFreeGroupsAreOneRowEach() {
        List<Event> events = EventGroups.saveFree(new ArrayList<>(), study(),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY));
        events.add(Event.free("Read", "", 30));

        assertEquals(2, EventRows.unplacedFree(events).size());
    }

    @Test
    public void recallingOneDayOfAFreeGroupRecallsEveryDayOfIt() {
        List<Event> saved = EventGroups.saveFree(new ArrayList<>(), study(),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        List<Event> placed = new ArrayList<>();
        int hour = 9;
        for (Event member : saved) {
            DayOfWeek day = member.getAllowedDays().iterator().next();
            placed.add(member.placedAt(day, hour++ * 60));
        }
        Event other = Event.free("Read", "", 30).placedAt(DayOfWeek.TUESDAY, 8 * 60);
        placed.add(other);

        List<Event> result = EventGroups.recalled(placed, Set.of(placed.get(0).getId()));

        for (Event event : result) {
            assertEquals(event.getId().equals(other.getId()), event.isPlaced());
        }
        assertEquals(1, FreeEventPlacer.countPlaced(result));
    }

    @Test
    public void recallingALoneFreeEventLeavesTheRestAlone() {
        Event first = Event.free("Read", "", 30).placedAt(DayOfWeek.TUESDAY, 8 * 60);
        Event second = Event.free("Write", "", 30).placedAt(DayOfWeek.WEDNESDAY, 8 * 60);

        List<Event> result = EventGroups.recalled(List.of(first, second), Set.of(first.getId()));

        assertFalse(result.get(0).isPlaced());
        assertTrue(result.get(1).isPlaced());
    }
}
