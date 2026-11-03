package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TutorialPagerTest {
    @Test
    public void nextAndBackMoveOnePageAndStopAtTheEnds() {
        TutorialPager pager = new TutorialPager(3, 0);
        assertTrue(pager.isFirst());
        pager.back();
        assertEquals(0, pager.index());

        pager.next();
        pager.next();
        assertTrue(pager.isLast());
        pager.next();
        assertEquals(2, pager.index());

        pager.back();
        assertEquals(1, pager.index());
        assertFalse(pager.isFirst());
        assertFalse(pager.isLast());
    }

    @Test
    public void aStartPageOutsideThePagesIsKeptInside() {
        assertEquals(2, new TutorialPager(3, 99).index());
        assertEquals(0, new TutorialPager(3, -4).index());
        assertEquals(1, new TutorialPager(3, 1).index());
    }

    @Test
    public void aTutorialNeedsAtLeastOnePage() {
        assertThrows(IllegalArgumentException.class, () -> new TutorialPager(0, 0));
    }
}
