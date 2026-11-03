package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.util.List;

import org.junit.Test;

public class BlockTextTest {
    /** Every character is one unit wide, so widths below count characters. */
    private static final BlockText.Measurer ONE_PER_CHAR = String::length;

    @Test
    public void lineCountGrowsWithEventLength() {
        assertEquals(1, BlockText.nameLinesFor(15));
        assertEquals(1, BlockText.nameLinesFor(45));
        assertEquals(2, BlockText.nameLinesFor(60));
        assertEquals(3, BlockText.nameLinesFor(90));
        assertEquals(3, BlockText.nameLinesFor(240));
    }

    @Test
    public void namesWrapAtWholeWords() {
        List<String> lines = BlockText.wrap("Intro to Computer Science", 12, 3, ONE_PER_CHAR);

        assertEquals(List.of("Intro to", "Computer", "Science"), lines);
    }

    @Test
    public void namesThatNeedMoreLinesEndWithEllipsis() {
        List<String> lines = BlockText.wrap("Intro to Computer Science", 12, 2, ONE_PER_CHAR);

        assertEquals(2, lines.size());
        assertEquals("Intro to", lines.get(0));
        assertEquals("Computer...", lines.get(1));
    }

    @Test
    public void wordLongerThanALineIsCutOff() {
        List<String> lines = BlockText.wrap("Supercalifragilistic", 10, 2, ONE_PER_CHAR);

        assertEquals(List.of("Superca..."), lines);
    }

    @Test
    public void shortNamesStayOnOneLine() {
        assertEquals(List.of("Gym"), BlockText.wrap("Gym", 20, 3, ONE_PER_CHAR));
    }

    @Test
    public void eventsShorterThanFifteenMinutesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Event.fixed("Call", "", DayOfWeek.MONDAY, 9 * 60, 9 * 60 + 10));
        assertThrows(IllegalArgumentException.class,
                () -> Event.free("Stretch", "", 10));
        assertEquals(15, Event.fixed("Call", "", DayOfWeek.MONDAY, 540, 555).getDurationMinutes());
    }

    @Test
    public void shortEventsSavedBeforeTheRuleStillLoad() {
        String oldFile = "id1\tFIXED\tCall\t\tMONDAY\t540\t10\t-\t-\n";

        assertEquals(1, EventCodec.decode(oldFile).size());
    }

    // Name lines are 10 units tall and the time line 8, so heights below count lines.

    @Test
    public void timeComesRightAfterTheFirstNameLine() {
        BlockText.Fit fit = BlockText.fit(60, 18, 10, 8);

        assertEquals(1, fit.nameLines);
        assertTrue(fit.showTime);
    }

    @Test
    public void extraNameLinesOnlyAfterTheTimeFits() {
        BlockText.Fit oneHour = BlockText.fit(60, 50, 10, 8);
        BlockText.Fit threeHours = BlockText.fit(180, 50, 10, 8);

        assertEquals(2, oneHour.nameLines);
        assertTrue(oneHour.showTime);
        assertEquals(3, threeHours.nameLines);
        assertTrue(threeHours.showTime);
    }

    @Test
    public void tightBlockShowsTheFirstLineWithoutTheTime() {
        BlockText.Fit fit = BlockText.fit(120, 14, 10, 8);

        assertEquals(1, fit.nameLines);
        assertFalse(fit.showTime);
    }

    @Test
    public void eventsUnderThirtyMinutesShowNoText() {
        BlockText.Fit fit = BlockText.fit(15, 100, 10, 8);

        assertEquals(0, fit.nameLines);
        assertFalse(fit.showTime);
    }
}