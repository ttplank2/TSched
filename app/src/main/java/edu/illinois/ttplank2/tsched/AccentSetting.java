package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Which accent the app uses, chosen in Settings: one of the color wheel's colors (saved as its hex)
 * or one of the secret choices (saved as {@code secret:} and its key). It starts as yellow. Saved
 * like events are, completely, before each save returns, so it survives closing the app.
 */
public final class AccentSetting {
    private static final String FILE_NAME = "accent_color.txt";

    private final AtomicFile file;

    public AccentSetting(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
    }

    /**
     * The saved choice: a wheel color's hex or a secret's {@code secret:} key. Yellow if none was
     * chosen or it can't be read.
     */
    public String loadKey() {
        try {
            return parseKey(new String(file.readFully(), StandardCharsets.UTF_8));
        } catch (FileNotFoundException none) {
            return format(EventColors.DEFAULT);
        } catch (IOException e) {
            return format(EventColors.DEFAULT);
        }
    }

    /**
     * The wheel color the accent stands for, such as for the default color of a new event: the
     * chosen color, or yellow if the choice is a secret one (those mix several colors).
     */
    public int load() {
        return parse(loadKey());
    }

    /** Saves {@code wheelColor} as the accent; false if it couldn't be written. */
    public boolean save(int wheelColor) {
        return write(format(wheelColor));
    }

    /** Saves the secret choice with this key as the accent; false if it couldn't be written. */
    public boolean saveSecret(String secretKey) {
        return write(AccentColors.SECRET_PREFIX + secretKey);
    }

    private boolean write(String text) {
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

    static String format(int wheelColor) {
        return Integer.toHexString(wheelColor);
    }

    /** Reads a saved choice as the wheel color it stands for: yellow for a secret or anything unknown. */
    static int parse(String text) {
        try {
            int color = EventColors.currentShade((int) Long.parseLong(text.trim(), 16));
            for (int wheelColor : EventColors.COLORS) {
                if (wheelColor == color) {
                    return color;
                }
            }
        } catch (NumberFormatException e) {
            // Falls through to the default.
        }
        return EventColors.DEFAULT;
    }

    /** Reads a saved choice as a key: a wheel color's hex, a secret's key, or yellow's hex. */
    static String parseKey(String text) {
        String trimmed = text.trim();
        if (AccentColors.secretIndex(trimmed) >= 0) {
            return trimmed;
        }
        return format(parse(trimmed));
    }
}
