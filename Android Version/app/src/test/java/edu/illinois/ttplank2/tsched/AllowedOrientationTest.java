package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;

import android.content.pm.ActivityInfo;

import org.junit.Test;

public class AllowedOrientationTest {
    private static final int PORTRAIT = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    private static final int LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    private static final int REVERSE_LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;

    @Test
    public void anUprightPhoneShowsPortraitFromEitherSideOfZero() {
        assertEquals(PORTRAIT, AllowedOrientation.forAngle(0, LANDSCAPE));
        assertEquals(PORTRAIT, AllowedOrientation.forAngle(20, LANDSCAPE));
        assertEquals(PORTRAIT, AllowedOrientation.forAngle(340, REVERSE_LANDSCAPE));
    }

    @Test
    public void aPhoneOnItsSideShowsTheMatchingLandscape() {
        assertEquals(LANDSCAPE, AllowedOrientation.forAngle(270, PORTRAIT));
        assertEquals(REVERSE_LANDSCAPE, AllowedOrientation.forAngle(90, PORTRAIT));
    }

    @Test
    public void anUpsideDownPhoneChangesNothing() {
        assertEquals(PORTRAIT, AllowedOrientation.forAngle(180, PORTRAIT));
        assertEquals(LANDSCAPE, AllowedOrientation.forAngle(180, LANDSCAPE));
        assertEquals(REVERSE_LANDSCAPE, AllowedOrientation.forAngle(160, REVERSE_LANDSCAPE));
        assertEquals(LANDSCAPE, AllowedOrientation.forAngle(205, LANDSCAPE));
    }

    @Test
    public void anUnknownAngleChangesNothing() {
        assertEquals(LANDSCAPE, AllowedOrientation.forAngle(-1, LANDSCAPE));
    }
}
