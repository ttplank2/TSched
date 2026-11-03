package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A setting that is on or off, kept in its own small file. It starts off. Saved like events are,
 * completely, before each save returns, so it survives closing the app.
 */
public final class OnOffSetting {
    private static final String ON = "on";
    private static final String OFF = "off";

    private final AtomicFile file;

    public OnOffSetting(Context context, String fileName) {
        file = new AtomicFile(new File(context.getFilesDir(), fileName));
    }

    /** Whether the setting is on; off if it was never saved or can't be read. */
    public boolean isOn() {
        try {
            return parse(new String(file.readFully(), StandardCharsets.UTF_8));
        } catch (FileNotFoundException none) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /** Saves the setting; false if it couldn't be written. */
    public boolean setOn(boolean on) {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(format(on).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
            return true;
        } catch (IOException e) {
            if (output != null) {
                file.failWrite(output);
            }
            return false;
        }
    }

    static String format(boolean on) {
        return on ? ON : OFF;
    }

    /** Reads what {@link #format} wrote; anything else counts as off. */
    static boolean parse(String text) {
        return ON.equals(text.trim());
    }
}
