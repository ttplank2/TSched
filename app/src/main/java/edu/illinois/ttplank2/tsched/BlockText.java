package edu.illinois.ttplank2.tsched;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides how an event's name wraps inside its calendar block. Names break only between
 * words. A one-hour event gets two lines, longer events up to three, and shorter events
 * one, never more than physically fit. When space is short, the first name line comes first,
 * then the time, then the second and third name lines. Events under 30 minutes show no text.
 * A word too long for a line, or a name that needs more lines than it gets, ends with "...".
 */
public final class BlockText {
    /** Measures how wide a piece of text is when drawn, in pixels. */
    public interface Measurer {
        float width(String text);
    }

    static final String ELLIPSIS = "...";
    /** Events shorter than this show no text in their block; it would not fit legibly. */
    public static final int MIN_MINUTES_FOR_TEXT = 30;

    private BlockText() {
    }

    /** How many name lines a block shows, and whether its time line fits too. */
    public static final class Fit {
        public final int nameLines;
        public final boolean showTime;

        Fit(int nameLines, boolean showTime) {
            this.nameLines = nameLines;
            this.showTime = showTime;
        }
    }

    /**
     * Shares a block's text height between name lines and the time line, in priority order:
     * first name line, time, second name line, third name line.
     */
    public static Fit fit(int durationMinutes, float textHeight, float nameLineHeight, float timeLineHeight) {
        if (durationMinutes < MIN_MINUTES_FOR_TEXT || textHeight < nameLineHeight) {
            return new Fit(0, false);
        }
        float left = textHeight - nameLineHeight;
        boolean showTime = left >= timeLineHeight;
        if (showTime) {
            left -= timeLineHeight;
        }
        int extraLines = Math.min(nameLinesFor(durationMinutes) - 1, (int) (left / nameLineHeight));
        return new Fit(1 + extraLines, showTime);
    }

    /** How many name lines an event of this length may use, before checking what fits. */
    public static int nameLinesFor(int durationMinutes) {
        if (durationMinutes < 60) {
            return 1;
        }
        return durationMinutes == 60 ? 2 : 3;
    }

    /**
     * Wraps {@code name} into at most {@code maxLines} lines no wider than {@code width}.
     * Always returns at least one line.
     */
    public static List<String> wrap(String name, float width, int maxLines, Measurer measurer) {
        List<String> lines = new ArrayList<>();
        String[] words = name.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        int next = 0;
        while (next < words.length && lines.size() < maxLines) {
            String word = words[next];
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (measurer.width(candidate) <= width) {
                line.setLength(0);
                line.append(candidate);
                next++;
            } else if (line.length() == 0) {
                // A single word wider than the whole line: cut it off.
                lines.add(cutToWidth(word, width, measurer));
                next++;
            } else {
                lines.add(line.toString());
                line.setLength(0);
            }
        }
        if (line.length() > 0 && lines.size() < maxLines) {
            lines.add(line.toString());
        }
        if (next < words.length && !lines.isEmpty()) {
            // More words than lines: mark the last line as cut off.
            int last = lines.size() - 1;
            lines.set(last, withEllipsis(lines.get(last), width, measurer));
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    /** {@code text} if it fits, otherwise as much of it as fits followed by "...". */
    static String cutToWidth(String text, float width, Measurer measurer) {
        if (measurer.width(text) <= width) {
            return text;
        }
        return withEllipsis(text, width, measurer);
    }

    private static String withEllipsis(String text, float width, Measurer measurer) {
        String base = text.endsWith(ELLIPSIS) ? text.substring(0, text.length() - ELLIPSIS.length()) : text;
        for (int end = base.length(); end > 0; end--) {
            String candidate = base.substring(0, end).trim() + ELLIPSIS;
            if (measurer.width(candidate) <= width) {
                return candidate;
            }
        }
        return ELLIPSIS;
    }
}
