package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EventColorsTest {
    @Test
    public void theDarkerShadesOfGreenTealBlueAndPurpleBecomeTheBrighterOnes() {
        assertEquals(EventColors.COLORS[4], EventColors.currentShade(0xFF66BB6A));
        assertEquals(EventColors.COLORS[5], EventColors.currentShade(0xFF26A69A));
        assertEquals(EventColors.COLORS[6], EventColors.currentShade(0xFF42A5F5));
        assertEquals(EventColors.COLORS[7], EventColors.currentShade(0xFFAB47BC));
        assertEquals(EventColors.COLORS[4], EventColors.onWheel(0xFF66BB6A));
        assertEquals(EventColors.COLORS[7], EventColors.onWheel(0xFFAB47BC));
    }

    @Test
    public void redMagentaAndTheBrightColorsAreUnchanged() {
        assertEquals(0xFFEF5350, EventColors.COLORS[0]);
        assertEquals(0xFFEC407A, EventColors.COLORS[8]);
        for (int color : EventColors.COLORS) {
            assertEquals(color, EventColors.currentShade(color));
            assertEquals(color, EventColors.onWheel(color));
        }
    }

    @Test
    public void removedColorsStillBecomeTheirReplacements() {
        assertEquals(EventColors.COLORS[5], EventColors.onWheel(0xFF4DD0E1));
        assertEquals(EventColors.COLORS[7], EventColors.onWheel(0xFF5C6BC0));
        assertEquals(EventColors.DEFAULT, EventColors.onWheel(0xFFA1887F));
        assertEquals(EventColors.DEFAULT, EventColors.onWheel(0xFF123456));
    }
}
