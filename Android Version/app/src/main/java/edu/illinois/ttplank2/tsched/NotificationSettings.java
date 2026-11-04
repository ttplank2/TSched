package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * What the user chose for event notifications in Settings: whether they are on, how many minutes
 * before an event they come (one number for every event), and which event colors get them. Each is
 * kept in its own small file, saved completely before each save returns, so they survive closing the
 * app. Notifications start off, ten minutes before, for no colors.
 */
public final class NotificationSettings {
    static final String ON_FILE = "notifications_on.txt";
    private static final String LEAD_FILE = "notify_lead.txt";
    private static final String COLORS_FILE = "notify_colors.txt";
    static final int DEFAULT_LEAD_MINUTES = 10;
    /** Zero means right when the event starts; the most is ten hours before. */
    static final int MIN_LEAD_MINUTES = 0;
    static final int MAX_LEAD_MINUTES = 600;

    private final OnOffSetting on;
    private final AtomicFile leadFile;
    private final AtomicFile colorsFile;

    public NotificationSettings(Context context) {
        on = new OnOffSetting(context, ON_FILE);
        leadFile = new AtomicFile(new File(context.getFilesDir(), LEAD_FILE));
        colorsFile = new AtomicFile(new File(context.getFilesDir(), COLORS_FILE));
    }

    public boolean isOn() {
        return on.isOn();
    }

    public boolean setOn(boolean turnedOn) {
        return on.setOn(turnedOn);
    }

    /** Minutes before an event that its notification comes; ten if none was saved. */
    public int leadMinutes() {
        return parseLead(read(leadFile));
    }

    public boolean saveLeadMinutes(int minutes) {
        return isValidLead(minutes) && write(leadFile, Integer.toString(minutes));
    }

    /** The colors whose events notify, as the wheel's current shades. */
    public Set<Integer> colors() {
        return parseColors(read(colorsFile));
    }

    public boolean saveColors(Set<Integer> colors) {
        return write(colorsFile, formatColors(colors));
    }

    static boolean isValidLead(int minutes) {
        return minutes >= MIN_LEAD_MINUTES && minutes <= MAX_LEAD_MINUTES;
    }

    /** Reads a saved lead time; anything that is not a whole number of minutes from 0 to 600 gives the default. */
    static int parseLead(String text) {
        try {
            int minutes = Integer.parseInt(text.trim());
            return isValidLead(minutes) ? minutes : DEFAULT_LEAD_MINUTES;
        } catch (NumberFormatException e) {
            return DEFAULT_LEAD_MINUTES;
        }
    }

    static String formatColors(Set<Integer> colors) {
        StringBuilder text = new StringBuilder();
        for (int color : colors) {
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(Integer.toHexString(color));
        }
        return text.toString();
    }

    /** Reads saved colors; entries that are not colors of the wheel (after moving old shades up) are left out. */
    static Set<Integer> parseColors(String text) {
        Set<Integer> colors = new HashSet<>();
        for (String entry : text.split(",")) {
            try {
                int color = EventColors.currentShade((int) Long.parseLong(entry.trim(), 16));
                for (int wheelColor : EventColors.COLORS) {
                    if (wheelColor == color) {
                        colors.add(color);
                    }
                }
            } catch (NumberFormatException e) {
                // Not a color: left out.
            }
        }
        return colors;
    }

    private static String read(AtomicFile file) {
        try {
            return new String(file.readFully(), StandardCharsets.UTF_8);
        } catch (FileNotFoundException none) {
            return "";
        } catch (IOException e) {
            return "";
        }
    }

    private static boolean write(AtomicFile file, String text) {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(text.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
            return true;
        } catch (IOException e) {
            if (output != null) {
                file.failWrite(output);
            }
            return false;
        }
    }
}
