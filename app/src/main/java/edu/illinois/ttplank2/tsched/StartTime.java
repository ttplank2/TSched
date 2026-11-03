package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The time of day the tall calendar opens each day scrolled to, set from Settings. It starts at
 * 7:00 AM. Saved like events are, completely, before each save returns, so it survives closing
 * the app.
 */
public final class StartTime {
    /** 7:00 AM, in minutes after midnight. */
    public static final int DEFAULT_MINUTE = 7 * 60;
    private static final String FILE_NAME = "start_time.txt";
    /** How much earlier than the chosen time the calendar scrolls to. */
    private static final int SCROLL_EARLIER_MINUTES = 5;

    private final AtomicFile file;

    public StartTime(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
    }

    /** The saved time in minutes after midnight, or 7:00 AM if none was saved or it is unreadable. */
    public int load() {
        try {
            return parse(new String(file.readFully(), StandardCharsets.UTF_8));
        } catch (FileNotFoundException none) {
            return DEFAULT_MINUTE;
        } catch (IOException e) {
            return DEFAULT_MINUTE;
        }
    }

    /** Saves {@code minute}; false if it couldn't be written. */
    public boolean save(int minute) {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(Integer.toString(minute).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
            return true;
        } catch (IOException e) {
            if (output != null) {
                file.failWrite(output);
            }
            return false;
        }
    }

    /**
     * Where the calendar actually scrolls to for a chosen start time: five minutes earlier, so a
     * start of 7:00 AM leaves the grid at 6:55 AM, with a little of the hour before showing. The
     * grid can't scroll above midnight, so the first five minutes of the day start at midnight.
     */
    public static int scrollMinute(int chosenMinute) {
        return Math.max(0, chosenMinute - SCROLL_EARLIER_MINUTES);
    }

    /** Reads a saved time; anything that isn't a minute of the day gives the default. */
    static int parse(String text) {
        try {
            int minute = Integer.parseInt(text.trim());
            return minute >= 0 && minute < Event.MINUTES_PER_DAY ? minute : DEFAULT_MINUTE;
        } catch (NumberFormatException e) {
            return DEFAULT_MINUTE;
        }
    }
}
