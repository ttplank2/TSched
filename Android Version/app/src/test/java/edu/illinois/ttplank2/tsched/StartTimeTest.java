package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StartTimeTest {
    @Test
    public void aSavedMinuteOfTheDayIsReadBack() {
        assertEquals(0, StartTime.parse("0"));
        assertEquals(6 * 60 + 45, StartTime.parse("405\n"));
        assertEquals(23 * 60 + 59, StartTime.parse("1439"));
    }

    @Test
    public void missingOrBrokenValuesGiveSevenAm() {
        assertEquals(7 * 60, StartTime.DEFAULT_MINUTE);
        assertEquals(StartTime.DEFAULT_MINUTE, StartTime.parse(""));
        assertEquals(StartTime.DEFAULT_MINUTE, StartTime.parse("soon"));
        assertEquals(StartTime.DEFAULT_MINUTE, StartTime.parse("-5"));
        assertEquals(StartTime.DEFAULT_MINUTE, StartTime.parse("1440"));
    }

    @Test
    public void theCalendarScrollsFiveMinutesBeforeTheChosenTime() {
        assertEquals(6 * 60 + 55, StartTime.scrollMinute(7 * 60));
        assertEquals(0, StartTime.scrollMinute(5));
        assertEquals(23 * 60 + 54, StartTime.scrollMinute(23 * 60 + 59));
    }

    @Test
    public void theGridNeverScrollsAboveMidnight() {
        assertEquals(0, StartTime.scrollMinute(0));
        assertEquals(0, StartTime.scrollMinute(4));
        assertEquals(1, StartTime.scrollMinute(6));
    }
}
