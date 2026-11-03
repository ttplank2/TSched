package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Whether taps and wheel clicks vibrate and make a sound, set from Settings. Both start off. Saved
 * like events are, completely, before each save returns, so they survive closing the app.
 */
public final class FeedbackSettings {
    private static final String FILE_NAME = "feedback.txt";
    private static final String VIBRATION_LINE = "vibration=";
    private static final String SOUND_LINE = "sound=";

    private final AtomicFile file;

    public FeedbackSettings(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
    }

    /** The saved switches as {vibration, sound}; both off if never saved or unreadable. */
    public boolean[] load() {
        try {
            return parse(new String(file.readFully(), StandardCharsets.UTF_8));
        } catch (FileNotFoundException none) {
            return new boolean[] {false, false};
        } catch (IOException e) {
            return new boolean[] {false, false};
        }
    }

    /** Saves the switches; false if they couldn't be written. */
    public boolean save(boolean vibration, boolean sound) {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(format(vibration, sound).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
            return true;
        } catch (IOException e) {
            if (output != null) {
                file.failWrite(output);
            }
            return false;
        }
    }

    static String format(boolean vibration, boolean sound) {
        return VIBRATION_LINE + onOff(vibration) + "\n" + SOUND_LINE + onOff(sound) + "\n";
    }

    /** Reads what {@link #format} wrote; anything missing or unrecognized counts as off. */
    static boolean[] parse(String text) {
        boolean vibration = false;
        boolean sound = false;
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.equals(VIBRATION_LINE + "on")) {
                vibration = true;
            } else if (trimmed.equals(SOUND_LINE + "on")) {
                sound = true;
            }
        }
        return new boolean[] {vibration, sound};
    }

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }
}
