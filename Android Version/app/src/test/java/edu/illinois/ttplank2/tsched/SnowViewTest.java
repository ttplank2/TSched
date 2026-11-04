package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SnowViewTest {
    @Test
    public void aFlakesPlaceDependsOnlyOnItsNumbersSoEveryScreenAgrees() {
        assertEquals(SnowView.unit(3, 7), SnowView.unit(3, 7), 0f);
    }

    @Test
    public void theNumbersStayWithinZeroAndOneAndDifferFromFlakeToFlake() {
        boolean different = false;
        for (int i = 0; i < 50; i++) {
            float value = SnowView.unit(i, 4);
            assertTrue(value >= 0f && value < 1f);
            different |= value != SnowView.unit(0, 4);
        }
        assertTrue(different);
    }
}
