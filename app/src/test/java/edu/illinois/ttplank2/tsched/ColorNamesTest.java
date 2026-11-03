package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class ColorNamesTest {
    @Test
    public void aNameSavedUnderADarkerShadeFollowsTheColorToItsBrighterShade() {
        String saved = "ffab47bc\tMath\nff66bb6a\tGym\n";

        java.util.Map<Integer, String> loaded = ColorNames.decode(saved);

        assertEquals("Math", loaded.get(EventColors.COLORS[7]));
        assertEquals("Gym", loaded.get(EventColors.COLORS[4]));
    }

    @Test
    public void aTypedNameIsKeptWithoutExtraSpaces() {
        assertEquals("Gym", ColorNames.resolve("  Gym ", "Red"));
    }

    @Test
    public void aBlankNameGoesBackToTheColorsOwnName() {
        assertEquals("Red", ColorNames.resolve("", "Red"));
        assertEquals("Red", ColorNames.resolve("   ", "Red"));
        assertEquals("Red", ColorNames.resolve(null, "Red"));
    }

    @Test
    public void savedNamesLoadBackTheSame() {
        Map<Integer, String> names = new HashMap<>();
        names.put(EventColors.COLORS[0], "Gym");
        names.put(EventColors.COLORS[5], "Study\ttime\\ \nlater");

        Map<Integer, String> loaded = ColorNames.decode(ColorNames.encode(names));

        assertEquals(names, loaded);
    }

    @Test
    public void damagedLinesAreSkippedAndTheRestLoad() {
        String saved = "garbage\nnot-hex\tName\n" + Integer.toHexString(EventColors.COLORS[2]) + "\tWork\n\n";

        Map<Integer, String> loaded = ColorNames.decode(saved);

        assertEquals(1, loaded.size());
        assertEquals("Work", loaded.get(EventColors.COLORS[2]));
    }

    @Test
    public void noSavedNamesMeansNothingRenamed() {
        assertTrue(ColorNames.decode("").isEmpty());
    }
}
