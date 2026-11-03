package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class NotificationSettingsTest {
    @Test
    public void theLeadTimeDefaultsToTenMinutes() {
        assertEquals(10, NotificationSettings.DEFAULT_LEAD_MINUTES);
        assertEquals(10, NotificationSettings.parseLead(""));
        assertEquals(10, NotificationSettings.parseLead("not a number"));
    }

    @Test
    public void aSavedLeadTimeIsReadBackWhenItIsInRange() {
        assertEquals(5, NotificationSettings.parseLead(" 5\n"));
        assertEquals(0, NotificationSettings.parseLead("0"));
        assertEquals(600, NotificationSettings.parseLead("600"));
    }

    @Test
    public void aLeadTimeOutOfRangeFallsBackToTheDefault() {
        assertEquals(10, NotificationSettings.parseLead("-3"));
        assertEquals(10, NotificationSettings.parseLead("601"));
        assertEquals(10, NotificationSettings.parseLead("1440"));
    }

    @Test
    public void onlyWholeMinutesFromZeroToSixHundredAreValid() {
        assertEquals(true, NotificationSettings.isValidLead(0));
        assertEquals(true, NotificationSettings.isValidLead(600));
        assertEquals(false, NotificationSettings.isValidLead(-1));
        assertEquals(false, NotificationSettings.isValidLead(601));
    }

    @Test
    public void chosenColorsAreSavedAndReadBack() {
        Set<Integer> chosen = new HashSet<>(Arrays.asList(EventColors.COLORS[1], EventColors.COLORS[6]));
        assertEquals(chosen, NotificationSettings.parseColors(NotificationSettings.formatColors(chosen)));
    }

    @Test
    public void unreadableColorsAndColorsNotOnTheWheelAreLeftOut() {
        assertEquals(new HashSet<Integer>(), NotificationSettings.parseColors(""));
        assertEquals(new HashSet<Integer>(), NotificationSettings.parseColors("garbage,ff123456"));
    }

    @Test
    public void anOldShadeIsReadAsItsCurrentShade() {
        Set<Integer> read = NotificationSettings.parseColors("ff66bb6a");
        assertEquals(new HashSet<>(Arrays.asList(EventColors.currentShade(0xFF66BB6A))), read);
    }
}
