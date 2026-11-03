package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class AccentColorsTest {
    private static final int RED = EventColors.COLORS[0];
    private static final int MAGENTA = EventColors.COLORS[8];

    @Test
    public void everyAccentExceptRedAndMagentaHasAtLeastSevenToOneContrastOnTheDarkBackground() {
        for (int color : EventColors.COLORS) {
            if (color == RED || color == MAGENTA) {
                continue;
            }
            int accent = AccentColors.adjusted(color);
            assertTrue("accent for " + Integer.toHexString(color),
                    AccentColors.contrast(accent, AccentColors.BACKGROUND) >= AccentColors.MIN_CONTRAST);
        }
    }

    @Test
    public void redAndMagentaAreTheAccentExactlyAsTheyAreEvenBelowSevenToOne() {
        assertEquals(RED, AccentColors.adjusted(RED));
        assertEquals(MAGENTA, AccentColors.adjusted(MAGENTA));
        assertTrue(AccentColors.contrast(RED, AccentColors.BACKGROUND) < AccentColors.MIN_CONTRAST);
        assertTrue(AccentColors.contrast(MAGENTA, AccentColors.BACKGROUND) < AccentColors.MIN_CONTRAST);
    }

    @Test
    public void everyOtherColorOfTheWheelIsAlreadyBrightEnough() {
        for (int color : EventColors.COLORS) {
            if (color != RED && color != MAGENTA) {
                assertEquals(color, AccentColors.adjusted(color));
            }
        }
    }

    @Test
    public void aColorThatIsBrightEnoughIsLeftAlone() {
        assertEquals(EventColors.DEFAULT, AccentColors.adjusted(EventColors.DEFAULT));
        assertEquals(0xFFC6E05A, AccentColors.adjusted(0xFFC6E05A));
    }

    @Test
    public void aTooDarkColorIsMadeLighterButStaysTheSameKindOfColor() {
        int purple = 0xFFAB47BC;
        int accent = AccentColors.adjusted(purple);
        // Lighter: every channel is at least what it was. The same color: red and blue still lead green.
        assertTrue(((accent >> 16) & 0xFF) >= ((purple >> 16) & 0xFF));
        assertTrue(((accent >> 8) & 0xFF) >= ((purple >> 8) & 0xFF));
        assertTrue((accent & 0xFF) >= (purple & 0xFF));
        assertTrue(((accent >> 8) & 0xFF) < ((accent >> 16) & 0xFF));
    }

    @Test
    public void everyWheelColorHasItsOwnThemeOverlayAndAnUnknownColorGetsYellows() {
        Set<Integer> styles = new HashSet<>();
        for (int color : EventColors.COLORS) {
            styles.add(AccentColors.styleFor(color));
        }
        assertEquals(EventColors.COLORS.length, styles.size());
        assertEquals(AccentColors.styleFor(EventColors.DEFAULT), AccentColors.styleFor(0xFF123456));
        assertNotEquals(AccentColors.styleFor(EventColors.COLORS[0]), AccentColors.styleFor(EventColors.COLORS[1]));
    }

    /** The overlays in accent_themes.xml must hold exactly the colors this class works out. */
    @Test
    public void theThemeOverlaysHoldTheAdjustedColors() throws IOException {
        File xml = new File("src/main/res/values/accent_themes.xml");
        if (!xml.exists()) {
            xml = new File("app/src/main/res/values/accent_themes.xml");
        }
        String text = new String(Files.readAllBytes(xml.toPath()), StandardCharsets.UTF_8);
        Matcher styles = Pattern.compile(
                "name=\"ThemeOverlay\\.TSched\\.Accent(\\d+)\"[^>]*>\\s*<item name=\"appAccent\">#([0-9A-Fa-f]{8})</item>")
                .matcher(text);
        int found = 0;
        while (styles.find()) {
            int index = Integer.parseInt(styles.group(1));
            int inFile = (int) Long.parseLong(styles.group(2), 16);
            assertEquals("overlay " + index, AccentColors.adjusted(EventColors.COLORS[index]), inFile);
            found++;
        }
        assertEquals(EventColors.COLORS.length, found);
    }

    @Test
    public void theSavedAccentIsReadBackAndAnythingElseIsYellow() {
        for (int color : EventColors.COLORS) {
            assertEquals(color, AccentSetting.parse(AccentSetting.format(color)));
        }
        assertEquals(EventColors.DEFAULT, AccentSetting.parse(""));
        assertEquals(EventColors.DEFAULT, AccentSetting.parse("not a color"));
        assertEquals(EventColors.DEFAULT, AccentSetting.parse("ff123456"));
        // An accent saved as one of the darker shades carries over to its brighter shade.
        assertEquals(EventColors.COLORS[7], AccentSetting.parse("ffab47bc"));
    }

    /** The secret overlays must hold the main, text and outline colors this class works out. */
    @Test
    public void theSecretOverlaysHoldTheSecretColors() throws IOException {
        File xml = new File("src/main/res/values/accent_themes.xml");
        if (!xml.exists()) {
            xml = new File("app/src/main/res/values/accent_themes.xml");
        }
        String text = new String(Files.readAllBytes(xml.toPath()), StandardCharsets.UTF_8);
        String overlay = "name=\"ThemeOverlay\\.TSched\\.Accent%s\"[^>]*>\\s*"
                + "<item name=\"appAccent\">#([0-9A-Fa-f]{8})</item>\\s*"
                + "<item name=\"appAccentText\">#([0-9A-Fa-f]{8})</item>\\s*"
                + "<item name=\"appAccentOutline\">#([0-9A-Fa-f]{8})</item>";
        for (int i = 0; i < AccentColors.SECRET_KEYS.length; i++) {
            {
                String name = AccentColors.SECRET_NAMES[i];
                Matcher m = Pattern.compile(String.format(overlay, name)).matcher(text);
                assertEquals(name, true, m.find());
                int[] expected = AccentColors.secretColors(i);
                for (int c = 0; c < 3; c++) {
                    assertEquals(name + " color " + c, expected[c], (int) Long.parseLong(m.group(c + 1), 16));
                }
            }
        }
    }

    @Test
    public void secretChoicesAreSavedWithAPrefixAndWheelColorsStayWheelColors() {
        assertEquals("secret:beige", AccentSetting.parseKey(" secret:beige\n"));
        assertEquals(AccentSetting.format(EventColors.DEFAULT), AccentSetting.parseKey("secret:unknown"));
        assertEquals(EventColors.DEFAULT, AccentSetting.parse("secret:beige"));
        assertEquals(AccentColors.HALLOWEEN_INDEX, AccentColors.secretIndex("secret:halloween"));
        assertEquals(-1, AccentColors.secretIndex(AccentSetting.format(EventColors.COLORS[3])));
    }
}
