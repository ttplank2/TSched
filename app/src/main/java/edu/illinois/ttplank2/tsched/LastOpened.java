package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Remembers when the app was last open, so counted events can lose a count for every end time
 * that passed while it was closed. Saved like events are, completely, before each save returns.
 */
public final class LastOpened {
    private static final String FILE_NAME = "last_opened.txt";

    private final AtomicFile file;

    public LastOpened(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
    }

    /** The saved time in milliseconds since 1970, or -1 if the app has never saved one. */
    public long load() {
        try {
            return Long.parseLong(new String(file.readFully(), StandardCharsets.UTF_8).trim());
        } catch (FileNotFoundException | NumberFormatException none) {
            return -1;
        } catch (IOException e) {
            return -1;
        }
    }

    /** Saves {@code millis} as the last time the app was open; false if it couldn't be written. */
    public boolean save(long millis) {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(Long.toString(millis).getBytes(StandardCharsets.UTF_8));
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
