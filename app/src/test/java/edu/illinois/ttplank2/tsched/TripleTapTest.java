package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TripleTapTest {
    /** The system's double-tap window, about 300 ms. */
    private static final long WINDOW = 300;

    @Test
    public void threeQuickTapsAreATripleTap() {
        TripleTap taps = new TripleTap(WINDOW);
        assertFalse(taps.tap(1000));
        assertFalse(taps.tap(1150));
        assertTrue(taps.tap(1300));
    }

    @Test
    public void twoTapsAreNotEnough() {
        TripleTap taps = new TripleTap(WINDOW);
        assertFalse(taps.tap(1000));
        assertFalse(taps.tap(1200));
    }

    @Test
    public void eachTapMustComeWithinTheWindowOfThePreviousOne() {
        TripleTap taps = new TripleTap(WINDOW);
        assertFalse(taps.tap(1000));
        assertFalse(taps.tap(1250));
        // 300 ms or less after the one before still counts, even though 500 ms have passed since the first.
        assertTrue(taps.tap(1550));
    }

    @Test
    public void aSlowThirdTapStartsOver() {
        TripleTap taps = new TripleTap(WINDOW);
        assertFalse(taps.tap(1000));
        assertFalse(taps.tap(1200));
        assertFalse(taps.tap(1501));
        assertFalse(taps.tap(1600));
        assertTrue(taps.tap(1700));
    }

    @Test
    public void tapsAreIgnoredForTheCooldownAfterATripleTap() {
        TripleTap taps = new TripleTap(WINDOW);
        taps.tap(1000);
        taps.tap(1100);
        assertTrue(taps.tap(1200));
        // A fourth tap right away must not start a new count or count as another triple.
        assertFalse(taps.tap(1250));
        assertFalse(taps.tap(1400));
        assertFalse(taps.tap(1450));
        // After the cooldown (300 ms) a fresh triple tap works again.
        assertFalse(taps.tap(1600));
        assertFalse(taps.tap(1700));
        assertTrue(taps.tap(1800));
    }

    @Test
    public void theCooldownIsTheSameAsTheDoubleTapsAndTheWindowIsTheSystemsDoubleTapTimeout() {
        assertEquals(TapOrDoubleTap.COOLDOWN_MS, TripleTap.COOLDOWN_MS);
    }
}
