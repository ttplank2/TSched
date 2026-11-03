package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotificationPlannerTest {
    private static final int ORANGE = EventColors.COLORS[1];
    private static final int BLUE = EventColors.COLORS[6];
    /** A Wednesday. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);

    private static Event fixed(String name, DayOfWeek day, int hour, int color) {
        return Event.fixed(name, "", day, hour * 60, hour * 60 + 60).withColor(color);
    }

    private static Set<Integer> colors(Integer... chosen) {
        return new HashSet<>(Arrays.asList(chosen));
    }

    @Test
    public void anEventOfAChosenColorNotifiesTheChosenTimeBeforeItStarts() {
        List<Event> events = Collections.singletonList(fixed("ECE", DayOfWeek.WEDNESDAY, 15, ORANGE));
        NotificationPlanner.Next next = NotificationPlanner.next(events, colors(ORANGE), 10, NOW);
        assertNotNull(next);
        assertEquals(LocalDateTime.of(2026, 11, 4, 14, 50), next.notifyAt);
        assertEquals("ECE", next.events.get(0).getName());
    }

    @Test
    public void everyChosenEventUsesTheSameLeadTime() {
        List<Event> events = Arrays.asList(
                fixed("ECE", DayOfWeek.WEDNESDAY, 15, ORANGE), fixed("Physics", DayOfWeek.WEDNESDAY, 17, BLUE));
        LocalDateTime first = NotificationPlanner.next(events, colors(ORANGE, BLUE), 25, NOW).notifyAt;
        LocalDateTime second = NotificationPlanner.next(events, colors(ORANGE, BLUE), 25,
                first.plusMinutes(1)).notifyAt;
        assertEquals(LocalDateTime.of(2026, 11, 4, 14, 35), first);
        assertEquals(LocalDateTime.of(2026, 11, 4, 16, 35), second);
    }

    @Test
    public void aColorThatIsNotChosenNeverNotifies() {
        List<Event> events = Collections.singletonList(fixed("Physics", DayOfWeek.WEDNESDAY, 15, BLUE));
        assertNull(NotificationPlanner.next(events, colors(ORANGE), 10, NOW));
    }

    @Test
    public void withNoColorsChosenNothingNotifies() {
        List<Event> events = Collections.singletonList(fixed("ECE", DayOfWeek.WEDNESDAY, 15, ORANGE));
        assertNull(NotificationPlanner.next(events, colors(), 10, NOW));
    }

    @Test
    public void anEventThatAlreadyStartedTodayNotifiesNextWeek() {
        List<Event> events = Collections.singletonList(fixed("ECE", DayOfWeek.WEDNESDAY, 9, ORANGE));
        assertEquals(LocalDateTime.of(2026, 11, 11, 8, 50),
                NotificationPlanner.next(events, colors(ORANGE), 10, NOW).notifyAt);
    }

    @Test
    public void ifTheAlertTimeHasPassedButTheEventHasNotStartedItWaitsForNextWeek() {
        // The event starts in 5 minutes, but the alert was due 5 minutes ago.
        List<Event> events = Collections.singletonList(
                Event.fixed("ECE", "", DayOfWeek.WEDNESDAY, 12 * 60 + 5, 13 * 60).withColor(ORANGE));
        assertEquals(LocalDateTime.of(2026, 11, 11, 11, 55),
                NotificationPlanner.next(events, colors(ORANGE), 10, NOW).notifyAt);
    }

    @Test
    public void theLeadTimeCanReachBackIntoThePreviousDay() {
        List<Event> events = Collections.singletonList(
                Event.fixed("Early", "", DayOfWeek.THURSDAY, 30, 90).withColor(ORANGE));
        assertEquals(LocalDateTime.of(2026, 11, 4, 23, 30),
                NotificationPlanner.next(events, colors(ORANGE), 60, NOW).notifyAt);
    }

    @Test
    public void theEarliestAlertAcrossTheWeekComesFirst() {
        List<Event> events = Arrays.asList(
                fixed("Later", DayOfWeek.FRIDAY, 9, ORANGE), fixed("Sooner", DayOfWeek.THURSDAY, 9, ORANGE));
        assertEquals("Sooner", NotificationPlanner.next(events, colors(ORANGE), 10, NOW).events.get(0).getName());
    }

    @Test
    public void eventsStartingAtTheSameTimeNotifyTogether() {
        List<Event> events = Arrays.asList(
                fixed("A", DayOfWeek.WEDNESDAY, 15, ORANGE), fixed("B", DayOfWeek.WEDNESDAY, 15, BLUE));
        assertEquals(2, NotificationPlanner.next(events, colors(ORANGE, BLUE), 10, NOW).events.size());
    }

    @Test
    public void aFreeEventNotifiesOnlyOnceItIsPlaced() {
        Event unplaced = Event.free("Study", "", 60).withColor(ORANGE);
        Event placed = unplaced.placedAt(DayOfWeek.WEDNESDAY, 16 * 60);
        List<Event> none = new ArrayList<>(Collections.singletonList(unplaced));
        assertNull(NotificationPlanner.next(none, colors(ORANGE), 10, NOW));
        assertEquals(LocalDateTime.of(2026, 11, 4, 15, 50),
                NotificationPlanner.next(Collections.singletonList(placed), colors(ORANGE), 10, NOW).notifyAt);
    }

    @Test
    public void colorsAreComparedByTheirCurrentShade() {
        int oldGreen = 0xFF66BB6A;
        List<Event> events = Collections.singletonList(fixed("Old", DayOfWeek.WEDNESDAY, 15, oldGreen));
        assertTrue(NotificationPlanner.next(events, colors(EventColors.currentShade(oldGreen)), 10, NOW) != null);
    }

    @Test
    public void aLeadTimeOfZeroNotifiesRightAtTheStart() {
        List<Event> events = Collections.singletonList(fixed("ECE", DayOfWeek.WEDNESDAY, 15, ORANGE));
        assertEquals(LocalDateTime.of(2026, 11, 4, 15, 0),
                NotificationPlanner.next(events, colors(ORANGE), 0, NOW).notifyAt);
    }

    @Test
    public void anEventTomorrowMorningNotifiesTheEveningBeforeWithTheLongestLeadTime() {
        // NOW is Wednesday noon. An 8 AM Thursday event, ten hours (600 minutes) ahead, is due at 10 PM tonight.
        List<Event> events = Collections.singletonList(fixed("Early", DayOfWeek.THURSDAY, 8, ORANGE));
        assertEquals(LocalDateTime.of(2026, 11, 4, 22, 0),
                NotificationPlanner.next(events, colors(ORANGE), 600, NOW).notifyAt);
    }

    @Test
    public void anEventJustAfterMidnightTomorrowNotifiesThisAfternoonWithTheLongestLeadTime() {
        List<Event> events = Collections.singletonList(
                Event.fixed("Late", "", DayOfWeek.THURSDAY, 60, 120).withColor(ORANGE));
        assertEquals(LocalDateTime.of(2026, 11, 4, 15, 0),
                NotificationPlanner.next(events, colors(ORANGE), 600, NOW).notifyAt);
    }
}
