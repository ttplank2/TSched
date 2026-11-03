package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ColorWheelStartTest {
    private static final int ORANGE = EventColors.COLORS[1];
    private static final int BLUE = EventColors.COLORS[6];

    @Test
    public void aWheelOpensOnTheColorAlreadyChosen() {
        assertEquals(6, ColorWheelDialog.startRowFor(BLUE, ORANGE));
    }

    @Test
    public void aWheelWithNothingChosenOpensOnTheAccentColor() {
        assertEquals(1, ColorWheelDialog.startRowFor(ColorWheelDialog.CLEAR, ORANGE));
        assertEquals(1, ColorWheelDialog.startRowFor(0xFF123456, ORANGE));
    }

    @Test
    public void ifNeitherIsOnTheWheelItOpensOnTheFirstColor() {
        assertEquals(0, ColorWheelDialog.startRowFor(0xFF123456, 0xFF654321));
    }
}
