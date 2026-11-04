package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FreeCountLabelTest {
    @Test
    public void countsUpToNineAreShownAsNumbers() {
        assertEquals("0", FreeCountLabel.of(0));
        assertEquals("9", FreeCountLabel.of(9));
    }

    @Test
    public void moreThanNineIsNinePlus() {
        assertEquals("9+", FreeCountLabel.of(10));
        assertEquals("9+", FreeCountLabel.of(250));
    }
}
