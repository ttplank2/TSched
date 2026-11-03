package edu.illinois.ttplank2.tsched;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Events saved together on several days form a group that is edited as one: fixed and counted
 * events happen on each day, and a free event gets one copy for each day, placeable only on that
 * day. Saving a group copies the form's details to every day chosen: days already in the group
 * keep their events, new days get new events, and days no longer chosen lose theirs.
 */
public final class EventGroups {
    private EventGroups() {
    }

    /** The events in {@code event}'s group, or just {@code event} if it stands alone. */
    public static List<Event> membersOf(List<Event> events, Event event) {
        List<Event> members = new ArrayList<>();
        if (!event.isGrouped()) {
            members.add(event);
            return members;
        }
        for (Event saved : events) {
            if (saved.getGroupId().equals(event.getGroupId())) {
                members.add(saved);
            }
        }
        return members;
    }

    /** {@code events} without any of {@code removed}, matched by id. */
    public static List<Event> withoutEvents(List<Event> events, Collection<Event> removed) {
        Set<String> removedIds = new HashSet<>();
        for (Event event : removed) {
            removedIds.add(event.getId());
        }
        List<Event> result = new ArrayList<>(events);
        result.removeIf(saved -> removedIds.contains(saved.getId()));
        return result;
    }

    /**
     * {@code events} with the placed free events whose ids are in {@code ids} taken off the
     * calendar, and with them every placed event in the same group: recalling one day of a free
     * event recalls all of its days.
     */
    public static List<Event> recalled(List<Event> events, Set<String> ids) {
        Set<String> recalledGroups = new HashSet<>();
        for (Event event : events) {
            if (ids.contains(event.getId()) && event.isGrouped()) {
                recalledGroups.add(event.getGroupId());
            }
        }
        List<Event> result = new ArrayList<>();
        for (Event event : events) {
            boolean recall = event.isPlaced()
                    && (ids.contains(event.getId()) || recalledGroups.contains(event.getGroupId()));
            result.add(recall ? event.unplaced() : event);
        }
        return result;
    }

    /** {@code events} without {@code event} and, if it is grouped, the rest of its group. */
    public static List<Event> withoutGroupOf(List<Event> events, Event event) {
        Set<String> removedIds = new HashSet<>();
        removedIds.add(event.getId());
        for (Event member : membersOf(events, event)) {
            removedIds.add(member.getId());
        }
        List<Event> result = new ArrayList<>(events);
        result.removeIf(saved -> removedIds.contains(saved.getId()));
        return result;
    }

    /**
     * {@code events} with the fixed event described by {@code details} saved on each of
     * {@code days}. {@code details} supplies the name, location, times, and color (its own day is
     * ignored); if its id is already saved, that event and its group are replaced.
     *
     * @throws IllegalArgumentException if {@code days} is empty
     */
    public static List<Event> saveFixed(List<Event> events, Event details, Set<DayOfWeek> days) {
        return saveFixed(events, details, days, false);
    }

    /**
     * Like {@link #saveFixed(List, Event, Set)}, and for a counted event (a fixed event that runs
     * out) {@code keepCounts} keeps the count each existing day has left instead of giving every
     * day the count in {@code details}; days new to the group still start with that count.
     */
    public static List<Event> saveFixed(List<Event> events, Event details, Set<DayOfWeek> days,
                                        boolean keepCounts) {
        if (days.isEmpty()) {
            throw new IllegalArgumentException("Pick at least one day for the event.");
        }
        Event original = null;
        for (Event saved : events) {
            if (saved.getId().equals(details.getId())) {
                original = saved;
            }
        }
        List<Event> members = original == null ? new ArrayList<>() : membersOf(events, original);
        String groupId = original != null && original.isGrouped() ? original.getGroupId()
                : days.size() > 1 ? UUID.randomUUID().toString() : Event.NO_GROUP;

        List<Event> result = new ArrayList<>(events);
        result.removeAll(members);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (days.contains(day)) {
                String id = idOnDay(members, day);
                Event dayEvent;
                if (details.getKind() == Event.Kind.COUNTED) {
                    int kept = keepCounts ? countOf(members, id) : -1;
                    int count = kept > 0 ? kept : details.getCount();
                    dayEvent = Event.countedWithId(id, details.getName(), details.getLocation(),
                            day, details.getStartMinute(), details.getDurationMinutes(), count);
                } else {
                    dayEvent = Event.fixedWithId(id, details.getName(), details.getLocation(),
                            day, details.getStartMinute(), details.getDurationMinutes());
                }
                result.add(dayEvent.withColor(details.getColor()).withGroupId(groupId));
            }
        }
        return result;
    }

    /**
     * {@code events} with the free event described by {@code details} saved once on each of
     * {@code days}: a group of free events, each one placeable only on its own day. {@code details}
     * supplies the name, location, length, and color; if its id is already saved, that event and its
     * group are replaced, and a member that was placed on a day still chosen stays placed where it
     * was if that spot still fits.
     *
     * @throws IllegalArgumentException if {@code days} is empty
     */
    public static List<Event> saveFree(List<Event> events, Event details, Set<DayOfWeek> days) {
        if (days.isEmpty()) {
            throw new IllegalArgumentException("Pick at least one day for the event.");
        }
        Event original = null;
        for (Event saved : events) {
            if (saved.getId().equals(details.getId())) {
                original = saved;
            }
        }
        List<Event> members = original == null ? new ArrayList<>() : membersOf(events, original);
        String groupId = original != null && original.isGrouped() ? original.getGroupId()
                : days.size() > 1 ? UUID.randomUUID().toString() : Event.NO_GROUP;

        List<Event> result = new ArrayList<>(events);
        result.removeAll(members);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (days.contains(day)) {
                String id = idOnDay(members, day);
                Event dayEvent = Event.freeWithId(id, details.getName(), details.getLocation(),
                        details.getDurationMinutes(), EnumSet.of(day))
                        .withColor(details.getColor()).withGroupId(groupId);
                for (Event member : members) {
                    if (member.getId().equals(id)) {
                        dayEvent = dayEvent.keepPlacementOf(member);
                    }
                }
                result.add(dayEvent);
            }
        }
        return result;
    }

    /** The count the member with {@code id} has left, or -1 if there is none or it isn't counted. */
    private static int countOf(List<Event> members, String id) {
        for (Event member : members) {
            if (member.getId().equals(id) && member.getKind() == Event.Kind.COUNTED) {
                return member.getCount();
            }
        }
        return -1;
    }

    /** The id of the member on {@code day} (placed there, or limited to it), or a new id if none. */
    private static String idOnDay(List<Event> members, DayOfWeek day) {
        for (Event member : members) {
            if (member.getDay() == day
                    || member.getDay() == null && member.getAllowedDays().equals(EnumSet.of(day))) {
                return member.getId();
            }
        }
        return UUID.randomUUID().toString();
    }
}
