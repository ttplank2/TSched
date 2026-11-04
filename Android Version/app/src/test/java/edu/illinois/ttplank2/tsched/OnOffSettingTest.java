package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OnOffSettingTest {
    @Test
    public void whatIsSavedIsWhatIsLoaded() {
        assertTrue(OnOffSetting.parse(OnOffSetting.format(true)));
        assertFalse(OnOffSetting.parse(OnOffSetting.format(false)));
    }

    @Test
    public void missingOrBrokenSettingsAreOff() {
        assertFalse(OnOffSetting.parse(""));
        assertFalse(OnOffSetting.parse("maybe"));
        assertEquals("off", OnOffSetting.format(false));
    }
}
