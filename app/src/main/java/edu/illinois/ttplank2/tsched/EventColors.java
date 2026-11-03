package edu.illinois.ttplank2.tsched;

/**
 * The colors an event can be, shown on the color wheel with their names. Events with no color
 * chosen show the default yellow.
 */
public final class EventColors {
    /** The app's yellow, used for events with no color chosen. */
    public static final int DEFAULT = 0xFFFFC93C;

    private static final int GREEN = 0xFF68BC6C;
    private static final int TEAL = 0xFF2BBDAF;
    private static final int BLUE = 0xFF5AB1F6;
    private static final int PURPLE = 0xFFCF96D9;

    /** Ten colors, chosen to stand out on the dark charcoal background. */
    public static final int[] COLORS = {
        0xFFEF5350, 0xFFFF8A3D, DEFAULT, 0xFFC6E05A,
        GREEN, TEAL, BLUE, PURPLE,
        0xFFEC407A, 0xFFF48FB1,
    };

    public static final String[] NAMES = {
        "Red", "Orange", "Yellow", "Lime",
        "Green", "Teal", "Blue", "Purple",
        "Magenta", "Pink",
    };

    /** The darker shades Green, Teal, Blue and Purple used to have, and the brighter shade of each now. */
    private static final int[][] RECOLORED = {
        {0xFF66BB6A, GREEN},
        {0xFF26A69A, TEAL},
        {0xFF42A5F5, BLUE},
        {0xFFAB47BC, PURPLE},
    };

    /** Colors that used to be on the wheel and the color each one became. */
    private static final int[][] REPLACEMENTS = {
        {0xFF4DD0E1, TEAL},       // cyan
        {0xFF5C6BC0, PURPLE},     // indigo
        {0xFFA1887F, DEFAULT},    // brown
        {0xFFE0E0E0, DEFAULT},    // white
        {0xFF90A4AE, DEFAULT},    // slate
    };

    private EventColors() {
    }

    /**
     * {@code color} if it is on the wheel; otherwise the color that replaced it (cyan became
     * teal, indigo became purple), or the default yellow for a color that has no replacement.
     */
    public static int onWheel(int color) {
        for (int offered : COLORS) {
            if (offered == color) {
                return color;
            }
        }
        int shade = currentShade(color);
        if (shade != color) {
            return shade;
        }
        for (int[] replacement : REPLACEMENTS) {
            if (replacement[0] == color) {
                return replacement[1];
            }
        }
        return DEFAULT;
    }

    /**
     * The brighter shade now used for {@code color} if it is one of the darker Green, Teal, Blue
     * or Purple the wheel used to have, so what was saved under it carries over; any other color
     * comes back unchanged.
     */
    public static int currentShade(int color) {
        for (int[] recolored : RECOLORED) {
            if (recolored[0] == color) {
                return recolored[1];
            }
        }
        return color;
    }

    /** The color's name, such as "Red", or "Custom" for a color not on the wheel. */
    public static String nameOf(int color) {
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i] == color) {
                return NAMES[i];
            }
        }
        return "Custom";
    }
}
