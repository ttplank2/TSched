package edu.illinois.ttplank2.tsched;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * One event in the week. A FIXED event has a day and start time and never moves.
 * A FREE event has only a length (and optionally the days it may happen on);
 * the app decides when it happens. Once the user approves a placement, a free event also has a
 * day and start time, stored in the same fields a fixed event uses, so the calendar shows it.
 */
public final class Event {
    /**
     * FIXED events happen at a set time. FREE events have a length and the app finds them a time.
     * COUNTED events are fixed events that run out: each time one ends it loses a count, and it
     * goes away at zero.
     */
    public enum Kind { FIXED, FREE, COUNTED }

    public static final int MINUTES_PER_DAY = 24 * 60;
    /** Marks an unset time, such as the start of a free event. */
    public static final int NO_TIME = -1;
    /** The shortest event the user may add or save from the form. */
    public static final int MIN_DURATION_MINUTES = 15;
    /** The group id of an event that is not grouped with any other. */
    public static final String NO_GROUP = "";
    /** The most times a counted event can repeat: one a week for a year. */
    public static final int MAX_COUNT = 52;

    private final String id;
    private final Kind kind;
    private final String name;
    private final String location;
    private final DayOfWeek day;
    private final int startMinute;
    private final int durationMinutes;
    private final Set<DayOfWeek> allowedDays;
    private final int color;
    private final String groupId;
    private final int count;

    private Event(String id, Kind kind, String name, String location, DayOfWeek day,
                  int startMinute, int durationMinutes,
                  Set<DayOfWeek> allowedDays, int color, String groupId, int count) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.location = location;
        this.day = day;
        this.startMinute = startMinute;
        this.durationMinutes = durationMinutes;
        this.allowedDays = allowedDays;
        this.color = color;
        this.groupId = groupId;
        this.count = count;
    }

    /**
     * Creates a fixed event that ends the same day. Times are minutes after midnight. (The form
     * makes overnight events by passing a longer duration to {@code fixedWithId}.)
     *
     * @throws IllegalArgumentException with a message fit to show the user
     */
    public static Event fixed(String name, String location, DayOfWeek day,
                              int startMinute, int endMinute) {
        requireMinimumLength(endMinute - startMinute);
        return fixedWithId(UUID.randomUUID().toString(), name, location, day,
                startMinute, endMinute - startMinute);
    }

    static Event fixedWithId(String id, String name, String location, DayOfWeek day,
                             int startMinute, int durationMinutes) {
        String trimmedName = requireName(name, "A fixed event needs a name.");
        if (day == null) {
            throw new IllegalArgumentException("Pick a day for the event.");
        }
        if (startMinute < 0 || startMinute >= MINUTES_PER_DAY) {
            throw new IllegalArgumentException("Pick a start time.");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("The end time must be after the start time.");
        }
        if (durationMinutes > MINUTES_PER_DAY) {
            throw new IllegalArgumentException("An event can be at most 24 hours long.");
        }
        return new Event(id, Kind.FIXED, trimmedName, trimToEmpty(location), day,
                startMinute, durationMinutes, Collections.emptySet(), EventColors.DEFAULT, NO_GROUP, 0);
    }

    /**
     * Creates a free event that may happen on any day.
     *
     * @throws IllegalArgumentException with a message fit to show the user
     */
    public static Event free(String name, String location, int durationMinutes) {
        return free(name, location, durationMinutes, Collections.emptySet());
    }

    /**
     * Creates a free event limited to {@code allowedDays}. An empty set, or every day of the
     * week, means it can happen on any day.
     *
     * @throws IllegalArgumentException with a message fit to show the user
     */
    public static Event free(String name, String location, int durationMinutes,
                             Set<DayOfWeek> allowedDays) {
        requireMinimumLength(durationMinutes);
        return freeWithId(UUID.randomUUID().toString(), name, location, durationMinutes,
                allowedDays);
    }

    static Event freeWithId(String id, String name, String location, int durationMinutes,
                            Set<DayOfWeek> allowedDays) {
        return placedFreeWithId(id, name, location, durationMinutes, allowedDays, null, NO_TIME);
    }

    /** A free event that may already be placed; pass null and NO_TIME when it is not. */
    static Event placedFreeWithId(String id, String name, String location, int durationMinutes,
                                  Set<DayOfWeek> allowedDays,
                                  DayOfWeek placedDay, int placedStart) {
        String trimmedName = requireName(name, "A free event needs a name.");
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("A free event needs a length.");
        }
        if (durationMinutes > MINUTES_PER_DAY) {
            throw new IllegalArgumentException("A free event can be at most 24 hours long.");
        }
        if ((placedDay == null) != (placedStart == NO_TIME)) {
            throw new IllegalArgumentException("A placed event needs both a day and a start time.");
        }
        if (placedDay != null && (placedStart < 0 || placedStart + durationMinutes > MINUTES_PER_DAY)) {
            throw new IllegalArgumentException("The event must fit inside one day.");
        }
        return new Event(id, Kind.FREE, trimmedName, trimToEmpty(location), placedDay,
                placedStart, durationMinutes, normalizeDays(allowedDays), EventColors.DEFAULT, NO_GROUP, 0);
    }

    /** This free event placed on {@code placedDay} at {@code placedStart}, keeping its id. */
    public Event placedAt(DayOfWeek placedDay, int placedStart) {
        return placedFreeWithId(id, name, location, durationMinutes, allowedDays,
                placedDay, placedStart).withColor(color).withGroupId(groupId);
    }

    /** This event with a different color (an ARGB value, such as one of {@link EventColors#COLORS}). */
    public Event withColor(int newColor) {
        return new Event(id, kind, name, location, day, startMinute, durationMinutes,
                allowedDays, newColor, groupId, count);
    }

    /**
     * Creates a counted event: a fixed event that happens {@code count} more times, losing a count
     * each time it ends.
     *
     * @throws IllegalArgumentException with a message fit to show the user
     */
    static Event countedWithId(String id, String name, String location, DayOfWeek day,
                               int startMinute, int durationMinutes, int count) {
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("The count must be from 1 to " + MAX_COUNT + ".");
        }
        Event fixed = fixedWithId(id, name, location, day, startMinute, durationMinutes);
        return new Event(id, Kind.COUNTED, fixed.name, fixed.location, day, startMinute,
                durationMinutes, fixed.allowedDays, fixed.color, fixed.groupId, count);
    }

    /** How many times this counted event has left; 0 for any other kind of event. */
    public int getCount() {
        return count;
    }

    /** This counted event with {@code newCount} left. */
    public Event withCount(int newCount) {
        return new Event(id, kind, name, location, day, startMinute, durationMinutes,
                allowedDays, color, groupId, newCount);
    }

    /** This event in the group {@code newGroupId}; see {@link #getGroupId()}. */
    public Event withGroupId(String newGroupId) {
        return new Event(id, kind, name, location, day, startMinute, durationMinutes,
                allowedDays, color, newGroupId, count);
    }

    /**
     * Events created together on several days share a group id, so editing one can update all of
     * them. {@link #NO_GROUP} if this event stands alone.
     */
    public String getGroupId() {
        return groupId;
    }

    public boolean isGrouped() {
        return !groupId.equals(NO_GROUP);
    }

    /** The event's color; {@link EventColors#DEFAULT} if the user never chose one. */
    public int getColor() {
        return color;
    }

    /**
     * This free event placed where {@code original} was, if {@code original} was placed and the
     * spot still works: its day is one of this event's allowed days and it still ends by midnight.
     * Otherwise this event, unplaced. Used when the user edits a placed free event.
     */
    public Event keepPlacementOf(Event original) {
        if (kind != Kind.FREE || !original.isPlaced()) {
            return this;
        }
        DayOfWeek placedDay = original.getDay();
        int placedStart = original.getStartMinute();
        if (!allowsDay(placedDay) || placedStart + durationMinutes > MINUTES_PER_DAY) {
            return this;
        }
        return placedAt(placedDay, placedStart);
    }

    /** This free event taken off the calendar, keeping its id, details, and color. */
    public Event unplaced() {
        return placedFreeWithId(id, name, location, durationMinutes, allowedDays, null, NO_TIME)
                .withColor(color).withGroupId(groupId);
    }

    /**
     * Whether this fixed event runs past midnight into the next day, such as sleep from 11 PM to
     * 7 AM. It then belongs to the day it starts on, and also shows on the next day.
     */
    public boolean crossesMidnight() {
        return (kind == Kind.FIXED || kind == Kind.COUNTED)
                && startMinute + durationMinutes > MINUTES_PER_DAY;
    }

    /** Whether this is a free event the user has approved a day and time for. */
    public boolean isPlaced() {
        return kind == Kind.FREE && day != null;
    }

    /**
     * Rejects new events shorter than {@link #MIN_DURATION_MINUTES}. Only checked when the user
     * creates or edits an event, so shorter events saved before this rule still load.
     *
     * @throws IllegalArgumentException with a message fit to show the user
     */
    public static void requireMinimumLength(int durationMinutes) {
        if (durationMinutes > 0 && durationMinutes < MIN_DURATION_MINUTES) {
            throw new IllegalArgumentException(
                    "Events must be at least " + MIN_DURATION_MINUTES + " minutes long.");
        }
    }

    private static String requireName(String name, String message) {
        String trimmed = trimToEmpty(name);
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private static String trimToEmpty(String text) {
        return text == null ? "" : text.trim();
    }

    public String getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    /** The day a fixed or placed free event is on; null for an unplaced free event. */
    public DayOfWeek getDay() {
        return day;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public int getEndMinute() {
        return startMinute + durationMinutes;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    /** Whether a free event is limited to some days rather than any day of the week. */
    public boolean hasDayLimit() {
        return !allowedDays.isEmpty();
    }

    /** The days a free event is limited to, in week order; empty if it can happen on any day. */
    public Set<DayOfWeek> getAllowedDays() {
        return allowedDays;
    }

    /** Whether a free event may happen on {@code candidate}. */
    public boolean allowsDay(DayOfWeek candidate) {
        return allowedDays.isEmpty() || allowedDays.contains(candidate);
    }

    /** No days or every day both mean "any day", stored as the empty set. */
    private static Set<DayOfWeek> normalizeDays(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty() || days.size() == DayOfWeek.values().length) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(days));
    }
}
