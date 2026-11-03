package edu.illinois.ttplank2.tsched;

/**
 * The accent color choices. Each of the color wheel's colors is a choice, brightened if it is too
 * dark to read on the app's dark background (Red and Magenta excepted, which are used as they are).
 * There are also a few secret choices (Grayscale, Beige, Halloween, Christmas, Fruity, Maroon and Gold), some of
 * which use several colors at once. Each choice has a theme overlay (in {@code accent_themes.xml})
 * that sets the accent attributes every accent-colored part of the app reads: {@code appAccent} for
 * buttons and selections, {@code appAccentText} for titles and labels, and {@code appAccentOutline}
 * for outlines.
 */
public final class AccentColors {
    /** The least contrast an accent may have against the dark background: 7 to 1. */
    public static final double MIN_CONTRAST = 7.0;
    /** The lighter of the app's two dark backgrounds ({@code charcoal_raised}), the harder to read on. */
    static final int BACKGROUND = 0xFF1F1F1F;
    /** How much lighter each try is, in lightness (0 to 1), when a color has to be brightened. */
    private static final double LIGHTNESS_STEP = 0.005;

    /** What a saved secret choice starts with, so it can't be mistaken for a color's hex. */
    public static final String SECRET_PREFIX = "secret:";

    static final int WHITE = 0xFFFFFFFF;
    /** The gray Fruity starts on, also used for popups while Fruity runs. */
    static final int GRAY = 0xFFBDBDBD;
    static final int BEIGE = 0xFFE8D8B8;
    /** A dark red with nothing but red in it (no green or blue), as Tyler asked. */
    static final int MAROON = 0xFFA00000;
    static final int GOLD = 0xFFBF9A2B;
    /** The whitish yellow Gold's titles shade to. */
    static final int WHITISH_YELLOW = 0xFFE2CB7C;
    private static final int RED = EventColors.COLORS[0];
    private static final int ORANGE = EventColors.COLORS[1];
    private static final int YELLOW = EventColors.COLORS[2];
    private static final int GREEN = EventColors.COLORS[4];
    private static final int BLUE = EventColors.COLORS[6];
    private static final int PURPLE = EventColors.COLORS[7];
    /** The seven colors Fruity mixes: the six fruit colors, with gray between yellow and green. */
    private static final int[] FRUITS = {RED, ORANGE, YELLOW, GRAY, GREEN, BLUE, PURPLE};

    /** The theme overlay for each color of the wheel, in the wheel's order. */
    private static final int[] STYLES = {
        R.style.ThemeOverlay_TSched_Accent0, R.style.ThemeOverlay_TSched_Accent1,
        R.style.ThemeOverlay_TSched_Accent2, R.style.ThemeOverlay_TSched_Accent3,
        R.style.ThemeOverlay_TSched_Accent4, R.style.ThemeOverlay_TSched_Accent5,
        R.style.ThemeOverlay_TSched_Accent6, R.style.ThemeOverlay_TSched_Accent7,
        R.style.ThemeOverlay_TSched_Accent8, R.style.ThemeOverlay_TSched_Accent9,
    };

    /** Where each secret choice is in the lists below, which are in the secret wheel's order. */
    static final int GRAYSCALE_INDEX = 0;
    static final int BEIGE_INDEX = 1;
    static final int MAROON_INDEX = 2;
    static final int HALLOWEEN_INDEX = 3;
    static final int CHRISTMAS_INDEX = 4;
    static final int GOLD_INDEX = 5;
    static final int FRUITY_INDEX = 6;

    /** The secret choices' keys, names, and the colors shown in their swatches, in the wheel's order. */
    static final String[] SECRET_KEYS = {"grayscale", "beige", "maroon", "halloween", "christmas", "gold", "fruity"};
    static final String[] SECRET_NAMES = {"Grayscale", "Beige", "Maroon", "Halloween", "Christmas", "Gold", "Fruity"};
    static final int[][] SECRET_SWATCHES = {
        {WHITE},
        {BEIGE},
        {MAROON},
        {ORANGE, YELLOW},
        {RED, GREEN},
        {GOLD, WHITISH_YELLOW},
        FRUITS,
    };
    /** The theme overlays of the secret choices; Fruity's is plain gray, where it always starts. */
    private static final int[] SECRET_STYLES = {
        R.style.ThemeOverlay_TSched_AccentGrayscale, R.style.ThemeOverlay_TSched_AccentBeige,
        R.style.ThemeOverlay_TSched_AccentMaroon, R.style.ThemeOverlay_TSched_AccentHalloween,
        R.style.ThemeOverlay_TSched_AccentChristmas, R.style.ThemeOverlay_TSched_AccentGold,
        R.style.ThemeOverlay_TSched_AccentFruity,
    };

    private AccentColors() {
    }

    /**
     * The accent shown for a wheel color: the color itself, or, if it has less than
     * {@link #MIN_CONTRAST} against the dark background, the same color made lighter until it has.
     * Red and Magenta are never changed. The wheel's other colors are already bright enough.
     */
    public static int adjusted(int color) {
        int rgb = color & 0xFFFFFF;
        if (keptAsIs(color) || contrast(rgb, BACKGROUND) >= MIN_CONTRAST) {
            return 0xFF000000 | rgb;
        }
        double[] hsl = toHsl(rgb);
        int lighter = rgb;
        while (contrast(lighter, BACKGROUND) < MIN_CONTRAST && hsl[2] < 1.0) {
            hsl[2] = Math.min(1.0, hsl[2] + LIGHTNESS_STEP);
            lighter = fromHsl(hsl[0], hsl[1], hsl[2]);
        }
        return 0xFF000000 | lighter;
    }

    /** Red and Magenta are the accent exactly as they are on the wheel, even below 7 to 1. */
    private static boolean keptAsIs(int color) {
        return color == EventColors.COLORS[0] || color == EventColors.COLORS[8];
    }

    /** The theme overlay for {@code wheelColor}; yellow's if it is not a color of the wheel. */
    public static int styleFor(int wheelColor) {
        for (int i = 0; i < EventColors.COLORS.length; i++) {
            if (EventColors.COLORS[i] == wheelColor) {
                return STYLES[i];
            }
        }
        return styleFor(EventColors.DEFAULT);
    }

    /** The theme overlay for a saved choice: a wheel color's hex, or a secret's key after the prefix. */
    public static int styleForKey(String key) {
        int secret = secretIndex(key);
        if (secret < 0) {
            return styleFor(wheelColorOf(key));
        }
        return SECRET_STYLES[secret];
    }

    /** The place of a saved choice among the secrets, or -1 if it is a color of the wheel. */
    static int secretIndex(String key) {
        if (key.startsWith(SECRET_PREFIX)) {
            String name = key.substring(SECRET_PREFIX.length());
            for (int i = 0; i < SECRET_KEYS.length; i++) {
                if (SECRET_KEYS[i].equals(name)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** The wheel color a saved hex stands for, or yellow if it isn't one. */
    private static int wheelColorOf(String key) {
        try {
            return EventColors.currentShade((int) Long.parseLong(key.trim(), 16));
        } catch (NumberFormatException e) {
            return EventColors.DEFAULT;
        }
    }

    /**
     * The three accent colors of a secret choice: {main, text, outline}. Grayscale and Beige use
     * one color for all three; Halloween is all orange (its titles also shade to yellow, see
     * {@link #titleColors}); Christmas is red and green; and Fruity starts on gray and then
     * cycles live (see {@link AccentPalette}).
     */
    static int[] secretColors(int secretIndex) {
        switch (secretIndex) {
            case GRAYSCALE_INDEX:
                return new int[] {WHITE, WHITE, WHITE};
            case BEIGE_INDEX:
                return new int[] {BEIGE, BEIGE, BEIGE};
            case MAROON_INDEX:
                return new int[] {MAROON, MAROON, MAROON};
            case HALLOWEEN_INDEX:
                return new int[] {ORANGE, ORANGE, ORANGE};
            case CHRISTMAS_INDEX:
                return new int[] {RED, GREEN, RED};
            case GOLD_INDEX:
                return new int[] {GOLD, GOLD, GOLD};
            default:
                return new int[] {GRAY, GRAY, GRAY};
        }
    }

    /** The six colors Fruity cycles through, in order. */
    static int[] fruitColors() {
        return FRUITS.clone();
    }

    /** Gray's place among {@link #fruitColors()}, where Fruity starts. */
    static final int GRAY_PLACE = 3;

    /**
     * The two colors a title's letters shade between (from the first to the second and back):
     * a secret choice's own two colors, except Halloween and Gold, whose buttons and text are all one color
     * (orange, gold) but whose titles shade to another (yellow, whitish yellow).
     */
    static int[] titleColors(int secretIndex) {
        if (secretIndex == HALLOWEEN_INDEX) {
            return new int[] {ORANGE, YELLOW};
        }
        if (secretIndex == GOLD_INDEX) {
            return new int[] {GOLD, WHITISH_YELLOW};
        }
        int[] colors = secretColors(secretIndex);
        return new int[] {colors[0], colors[1]};
    }

    /** The two colors of Christmas titles, whose letters take turns like the stripes of a candy cane. */
    static int[] candyCaneColors() {
        return new int[] {RED, WHITE};
    }

    static boolean isHalloween(int secretIndex) {
        return secretIndex == HALLOWEEN_INDEX;
    }

    static boolean isChristmas(int secretIndex) {
        return secretIndex == CHRISTMAS_INDEX;
    }

    static boolean isGold(int secretIndex) {
        return secretIndex == GOLD_INDEX;
    }

    static boolean isFruity(int secretIndex) {
        return secretIndex == FRUITY_INDEX;
    }

    /** The contrast ratio between two colors, from 1 (the same) to 21 (black on white). */
    public static double contrast(int colorA, int colorB) {
        double a = luminance(colorA);
        double b = luminance(colorB);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * linear((color >> 16) & 0xFF)
                + 0.7152 * linear((color >> 8) & 0xFF)
                + 0.0722 * linear(color & 0xFF);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    /** Hue, saturation and lightness, each from 0 to 1. */
    private static double[] toHsl(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double lightness = (max + min) / 2;
        if (max == min) {
            return new double[] {0, 0, lightness};
        }
        double delta = max - min;
        double saturation = lightness > 0.5 ? delta / (2 - max - min) : delta / (max + min);
        double hue;
        if (max == r) {
            hue = (g - b) / delta + (g < b ? 6 : 0);
        } else if (max == g) {
            hue = (b - r) / delta + 2;
        } else {
            hue = (r - g) / delta + 4;
        }
        return new double[] {hue / 6, saturation, lightness};
    }

    private static int fromHsl(double hue, double saturation, double lightness) {
        if (saturation == 0) {
            int gray = (int) Math.round(lightness * 255);
            return (gray << 16) | (gray << 8) | gray;
        }
        double q = lightness < 0.5 ? lightness * (1 + saturation)
                : lightness + saturation - lightness * saturation;
        double p = 2 * lightness - q;
        int r = (int) Math.round(channel(p, q, hue + 1.0 / 3) * 255);
        int g = (int) Math.round(channel(p, q, hue) * 255);
        int b = (int) Math.round(channel(p, q, hue - 1.0 / 3) * 255);
        return (r << 16) | (g << 8) | b;
    }

    private static double channel(double p, double q, double t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1.0 / 6) {
            return p + (q - p) * 6 * t;
        }
        if (t < 1.0 / 2) {
            return q;
        }
        if (t < 2.0 / 3) {
            return p + (q - p) * (2.0 / 3 - t) * 6;
        }
        return p;
    }
}
