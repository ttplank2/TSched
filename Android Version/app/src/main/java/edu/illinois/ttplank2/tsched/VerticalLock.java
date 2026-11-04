package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Whether the calendar is locked to the tall (vertical) view, set from Settings. Saved like
 * events are, completely, before each save returns, so it survives closing the app.
 */
public final class VerticalLock {
    private static final String FILE_NAME = "vertical_lock.txt";
    private static final String LOCKED = "locked";

    private final AtomicFile file;

    public VerticalLock(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
    }

    /** Whether the calendar is locked; false if it never was or the setting can't be read. */
    public boolean isLocked() {
        try {
            return LOCKED.equals(new String(file.readFully(), StandardCharsets.UTF_8).trim());
        } catch (FileNotFoundException none) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /** Saves the setting; false if it couldn't be written. */
    public boolean setLocked(boolean locked) {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write((locked ? LOCKED : "unlocked").getBytes(StandardCharsets.UTF_8));
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
