package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The names shown for the colors. Each color starts with its own name ("Red", "Teal"); the user
 * can rename any of them, and a blank name goes back to the original. Names are saved in a file
 * on the phone the same way events are: each save is written out completely before it returns, so
 * closing the app right afterward can't lose them. Every screen shares one copy of the names.
 */
public final class ColorNames {
    private static final String FILE_NAME = "color_names.txt";
    /** Where names were kept before they moved to a file; read once to carry them over. */
    private static final String OLD_PREFS_NAME = "color_names";
    private static final Object LOCK = new Object();
    /** The names the user chose, by color; loaded once and then kept up to date. */
    private static Map<Integer, String> custom;

    private final AtomicFile file;

    public ColorNames(Context context) {
        Context app = context.getApplicationContext();
        file = new AtomicFile(new File(app.getFilesDir(), FILE_NAME));
        synchronized (LOCK) {
            if (custom == null) {
                custom = load(app);
            }
        }
    }

    /** The user's name for {@code color}, or its original name if they never changed it. */
    public String nameOf(int color) {
        synchronized (LOCK) {
            String name = custom.get(color);
            return name != null ? name : EventColors.nameOf(color);
        }
    }

    /** Whether the user gave {@code color} a name of their own, so it isn't showing its original. */
    public boolean hasCustomName(int color) {
        synchronized (LOCK) {
            return custom.containsKey(color);
        }
    }

    /**
     * Renames each of {@code colors} to the matching text in {@code typed}; a blank one restores
     * that color's original name.
     *
     * @return false if the names couldn't be saved, in which case nothing changed
     */
    public boolean renameAll(int[] colors, String[] typed) {
        synchronized (LOCK) {
            Map<Integer, String> updated = new HashMap<>(custom);
            for (int i = 0; i < colors.length; i++) {
                String original = EventColors.nameOf(colors[i]);
                String name = resolve(typed[i], original);
                if (name.equals(original)) {
                    updated.remove(colors[i]);
                } else {
                    updated.put(colors[i], name);
                }
            }
            if (!write(file, updated)) {
                return false;
            }
            custom = updated;
            return true;
        }
    }

    /** The name to keep for what the user typed: {@code typed} trimmed, or {@code original} if blank. */
    static String resolve(String typed, String original) {
        String trimmed = typed == null ? "" : typed.trim();
        return trimmed.isEmpty() ? original : trimmed;
    }

    /** One line per renamed color: its color value in hex, a tab, then the name. */
    static String encode(Map<Integer, String> names) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<Integer, String> entry : names.entrySet()) {
            text.append(Integer.toHexString(entry.getKey())).append('\t')
                    .append(escape(entry.getValue())).append('\n');
        }
        return text.toString();
    }

    /** Reads names back, skipping any line that is damaged. */
    static Map<Integer, String> decode(String text) {
        Map<Integer, String> names = new HashMap<>();
        for (String line : text.split("\n")) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 2) {
                continue;
            }
            try {
                String name = unescape(fields[1]).trim();
                if (!name.isEmpty()) {
                    names.put(EventColors.currentShade((int) Long.parseLong(fields[0], 16)), name);
                }
            } catch (NumberFormatException ignored) {
                // A damaged line should not cost the user their other names.
            }
        }
        return names;
    }

    private static Map<Integer, String> load(Context context) {
        File saved = new File(context.getFilesDir(), FILE_NAME);
        AtomicFile file = new AtomicFile(saved);
        try {
            return decode(new String(file.readFully(), StandardCharsets.UTF_8));
        } catch (FileNotFoundException none) {
            return carryOverOldNames(context, file);
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    /** Names saved by the earlier version are copied into the file, once. */
    private static Map<Integer, String> carryOverOldNames(Context context, AtomicFile file) {
        Map<Integer, String> names = new HashMap<>();
        SharedPreferences old = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : old.getAll().entrySet()) {
            try {
                if (entry.getValue() instanceof String) {
                    names.put(EventColors.currentShade((int) Long.parseLong(entry.getKey(), 16)),
                            (String) entry.getValue());
                }
            } catch (NumberFormatException ignored) {
                // Not a color name; leave it.
            }
        }
        if (!names.isEmpty()) {
            write(file, names);
        }
        return names;
    }

    private static boolean write(AtomicFile file, Map<Integer, String> names) {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(encode(names).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
            return true;
        } catch (IOException e) {
            if (output != null) {
                file.failWrite(output);
            }
            return false;
        }
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
