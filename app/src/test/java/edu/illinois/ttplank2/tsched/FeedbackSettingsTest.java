package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class FeedbackSettingsTest {
    @Test
    public void whatIsSavedIsWhatIsLoaded() {
        for (boolean vibration : new boolean[] {false, true}) {
            for (boolean sound : new boolean[] {false, true}) {
                boolean[] loaded = FeedbackSettings.parse(FeedbackSettings.format(vibration, sound));
                assertArrayEquals(new boolean[] {vibration, sound}, loaded);
            }
        }
    }

    @Test
    public void missingOrUnreadableSettingsAreOff() {
        assertArrayEquals(new boolean[] {false, false}, FeedbackSettings.parse(""));
        assertArrayEquals(new boolean[] {false, false}, FeedbackSettings.parse("vibration=maybe\n???"));
    }

    @Test
    public void oneSwitchDoesNotTurnOnTheOther() {
        assertArrayEquals(new boolean[] {true, false}, FeedbackSettings.parse("vibration=on\nsound=off\n"));
        assertArrayEquals(new boolean[] {false, true}, FeedbackSettings.parse("sound=on\n"));
    }
}
