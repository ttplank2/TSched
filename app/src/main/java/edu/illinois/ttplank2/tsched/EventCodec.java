package edu.illinois.ttplank2.tsched;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Turns events into text for saving and back. Each event is one line of tab-separated
 * fields; tabs, newlines, and backslashes inside names are escaped.
 */
public final class EventCodec {
    private static final String NO_DAY = "-";
    /** Fields per line now; older files have fewer (no count, no group id, or no color either). */
    private static final int FIELD_COUNT = 12;
    private static final int FIELD_COUNT_BEFORE_COUNTS = 11;
    private static final int FIELD_COUNT_BEFORE_GROUPS = 10;
    private static final int FIELD_COUNT_BEFORE_COLORS = 9;
    private static final String UNNAMED_FREE_EVENT = "Free time";

    private EventCodec() {
    }

    public static String encode(List<Event> events) {
        StringBuilder text = new StringBuilder();
        for (Event event : events) {
            text.append(escape(event.getId())).append('\t')
                    .append(event.getKind().name()).append('\t')
                    .append(escape(event.getName())).append('\t')
                    .append(escape(event.getLocation())).append('\t')
                    .append(event.getDay() == null ? NO_DAY : event.getDay().name()).append('\t')
                    .append(event.getStartMinute()).append('\t')
                    .append(event.getDurationMinutes()).append('\t')
                    .append(dayList(event.getAllowedDays())).append('\t')
                    .append(NO_DAY).append('\t')
                    .append(Integer.toHexString(event.getColor())).append('\t')
                    .append(escape(event.getGroupId())).append('\t')
                    .append(event.getCount()).append('\n');
        }
        return text.toString();
    }

    /** Reads events back, skipping any line that is damaged or no longer valid. */
    public static List<Event> decode(String text) {
        List<Event> events = new ArrayList<>();
        for (String line : text.split("\n")) {
            String[] fields = line.split("\t", -1);
            if (fields.length != FIELD_COUNT && fields.length != FIELD_COUNT_BEFORE_COUNTS
                    && fields.length != FIELD_COUNT_BEFORE_GROUPS
                    && fields.length != FIELD_COUNT_BEFORE_COLORS) {
                continue;
            }
            try {
                events.add(decodeFields(fields));
            } catch (IllegalArgumentException ignored) {
                // A corrupt line should not cost the user every other event.
            }
        }
        return events;
    }

    private static Event decodeFields(String[] fields) {
        int color = fields.length >= FIELD_COUNT_BEFORE_GROUPS
                ? (int) Long.parseLong(fields[9], 16) : EventColors.DEFAULT;
        // Colors taken off the wheel since the event was saved become their replacements.
        color = EventColors.onWheel(color);
        String groupId = fields.length >= FIELD_COUNT_BEFORE_COUNTS
                ? unescape(fields[10]) : Event.NO_GROUP;
        return decodeWithoutColor(fields).withColor(color).withGroupId(groupId);
    }

    private static Event decodeWithoutColor(String[] fields) {
        String id = unescape(fields[0]);
        Event.Kind kind = Event.Kind.valueOf(fields[1]);
        String name = unescape(fields[2]);
        String location = unescape(fields[3]);
        int startMinute = Integer.parseInt(fields[5]);
        int durationMinutes = Integer.parseInt(fields[6]);
        if (kind == Event.Kind.FIXED) {
            DayOfWeek day = DayOfWeek.valueOf(fields[4]);
            return Event.fixedWithId(id, name, location, day, startMinute, durationMinutes);
        }
        if (kind == Event.Kind.COUNTED) {
            if (fields.length != FIELD_COUNT) {
                throw new IllegalArgumentException("A counted event needs a count.");
            }
            DayOfWeek day = DayOfWeek.valueOf(fields[4]);
            return Event.countedWithId(id, name, location, day, startMinute, durationMinutes,
                    Integer.parseInt(fields[11]));
        }
        if (name.trim().isEmpty()) {
            // Free events saved before names were required keep a readable name.
            name = UNNAMED_FREE_EVENT;
        }
        return Event.placedFreeWithId(id, name, location, durationMinutes,
                parseAllowedDays(fields[7], fields[8]), parseDay(fields[4]), startMinute);
    }

    /** Allowed days as comma-separated names in week order, or "-" for any day. */
    private static String dayList(Set<DayOfWeek> days) {
        if (days.isEmpty()) {
            return NO_DAY;
        }
        StringBuilder names = new StringBuilder();
        for (DayOfWeek day : days) {
            names.append(names.length() == 0 ? "" : ",").append(day.name());
        }
        return names.toString();
    }

    /**
     * Reads the allowed days. Files now hold a comma-separated list in the first field and "-" in
     * the second; older files held the first and last day of a range (which could wrap past
     * Sunday) in the two fields.
     */
    private static Set<DayOfWeek> parseAllowedDays(String first, String second) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        DayOfWeek last = parseDay(second);
        DayOfWeek start = parseDay(first);
        if (start != null && last != null) {
            for (DayOfWeek day = start; ; day = day.plus(1)) {
                days.add(day);
                if (day == last) {
                    break;
                }
            }
            return days;
        }
        for (String name : first.split(",")) {
            DayOfWeek day = parseDay(name);
            if (day != null) {
                days.add(day);
            }
        }
        return days;
    }

    /**
     * Reads a day field. Files saved before day ranges existed hold time-window minutes
     * here instead; those (and "-") mean any day.
     */
    private static DayOfWeek parseDay(String field) {
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.name().equals(field)) {
                return day;
            }
        }
        return null;
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescape(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                result.append(next == 't' ? '\t' : next == 'n' ? '\n' : next);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
