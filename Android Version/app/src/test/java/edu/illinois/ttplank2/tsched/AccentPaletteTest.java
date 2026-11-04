package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccentPaletteTest {
    private static final int FIRST = 0xFF000000;
    private static final int SECOND = 0xFF0000FF;

    @Test
    public void aTitleStartsAndEndsOnTheFirstColorAndPeaksOnTheSecond() {
        assertEquals(FIRST, AccentPalette.letterColor(0, 5, FIRST, SECOND));
        assertEquals(SECOND, AccentPalette.letterColor(2, 5, FIRST, SECOND));
        assertEquals(FIRST, AccentPalette.letterColor(4, 5, FIRST, SECOND));
    }

    @Test
    public void aOneLetterTitleUsesTheFirstColor() {
        assertEquals(FIRST, AccentPalette.letterColor(0, 1, FIRST, SECOND));
    }

    @Test
    public void halloweenAndChristmasUseTwoColorsAndFruityOne() {
        for (int secret : new int[] {AccentColors.HALLOWEEN_INDEX, AccentColors.CHRISTMAS_INDEX}) {
            int[] colors = AccentColors.secretColors(secret);
            assertEquals(colors[0], colors[2]);
        }
        int[] fruit = AccentColors.secretColors(AccentColors.FRUITY_INDEX);
        assertEquals(fruit[0], fruit[1]);
        assertEquals(fruit[1], fruit[2]);
    }

    @Test
    public void fruityStartsOnGray() {
        assertEquals(AccentColors.fruitColors()[AccentColors.GRAY_PLACE], AccentColors.secretColors(AccentColors.FRUITY_INDEX)[0]);
    }

    @Test
    public void christmasTitleLettersTakeTurnsBetweenRedAndWhite() {
        int[] stripes = AccentColors.candyCaneColors();
        assertEquals(stripes[0], AccentPalette.alternatingColor(0, stripes[0], stripes[1]));
        assertEquals(stripes[1], AccentPalette.alternatingColor(1, stripes[0], stripes[1]));
        assertEquals(stripes[0], AccentPalette.alternatingColor(2, stripes[0], stripes[1]));
    }

    @Test
    public void halloweenIsOrangeWithTitlesShadingToYellowAndNoPurple() {
        int orange = AccentColors.secretColors(AccentColors.HALLOWEEN_INDEX)[0];
        for (int color : AccentColors.secretColors(AccentColors.HALLOWEEN_INDEX)) {
            assertEquals(orange, color);
        }
        int[] title = AccentColors.titleColors(AccentColors.HALLOWEEN_INDEX);
        assertEquals(orange, title[0]);
        assertEquals(AccentColors.fruitColors()[AccentColors.GRAY_PLACE - 1], title[1]);
        for (int color : AccentColors.SECRET_SWATCHES[AccentColors.HALLOWEEN_INDEX]) {
            assertTrue(color != AccentColors.fruitColors()[6]);
        }
    }

    @Test
    public void maroonIsADarkRedWithNothingButRedInIt() {
        int[] colors = AccentColors.secretColors(AccentColors.MAROON_INDEX);
        assertEquals(colors[0], colors[1]);
        assertEquals(colors[1], colors[2]);
        assertEquals(0, colors[0] & 0xFF);
        assertEquals(0, (colors[0] >> 8) & 0xFF);
        int red = (colors[0] >> 16) & 0xFF;
        assertTrue(red > 100 && red < 200);
        assertEquals("Maroon", AccentColors.SECRET_NAMES[AccentColors.MAROON_INDEX]);
        assertEquals(AccentColors.MAROON_INDEX, AccentColors.secretIndex("secret:maroon"));
    }

    @Test
    public void goldIsAllGoldExceptTitlesWhichShadeToAWhitishYellow() {
        int[] colors = AccentColors.secretColors(AccentColors.GOLD_INDEX);
        assertEquals(AccentColors.GOLD, colors[0]);
        assertEquals(colors[0], colors[1]);
        assertEquals(colors[1], colors[2]);
        int[] title = AccentColors.titleColors(AccentColors.GOLD_INDEX);
        assertEquals(AccentColors.GOLD, title[0]);
        assertEquals(AccentColors.WHITISH_YELLOW, title[1]);
        // The whitish yellow is lighter than the gold and still yellow (red and green high, blue lower).
        assertTrue(((title[1] >> 16) & 0xFF) > ((title[0] >> 16) & 0xFF));
        assertTrue((title[1] & 0xFF) < ((title[1] >> 8) & 0xFF));
        assertEquals("Gold", AccentColors.SECRET_NAMES[AccentColors.GOLD_INDEX]);
        assertEquals(AccentColors.GOLD_INDEX, AccentColors.secretIndex("secret:gold"));
    }

    @Test
    public void theSecretWheelIsInTheOrderTylerChose() {
        assertEquals("[Grayscale, Beige, Maroon, Halloween, Christmas, Gold, Fruity]",
                java.util.Arrays.toString(AccentColors.SECRET_NAMES));
        assertEquals(AccentColors.SECRET_KEYS.length, AccentColors.SECRET_NAMES.length);
        assertEquals(AccentColors.SECRET_KEYS.length, AccentColors.SECRET_SWATCHES.length);
    }

    @Test
    public void aTwoLetterWordStillShinesBothLettersTowardTheSecondColor() {
        int first = AccentPalette.letterColor(0, 2, FIRST, SECOND);
        int second = AccentPalette.letterColor(1, 2, FIRST, SECOND);
        assertTrue(first != FIRST);
        assertTrue(second != FIRST);
        assertEquals(first, second);
    }

    @Test
    public void aThreeLetterWordPeaksOnItsMiddleLetter() {
        assertEquals(SECOND, AccentPalette.letterColor(1, 3, FIRST, SECOND));
        assertTrue(AccentPalette.letterColor(0, 3, FIRST, SECOND) != SECOND);
    }
}
