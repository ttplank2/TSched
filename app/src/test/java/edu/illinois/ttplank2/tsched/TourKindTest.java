package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TourKindTest {
    @Test
    public void everyTourHasStepsAndEveryStepHasWordsToShow() {
        for (TourKind kind : TourKind.values()) {
            assertTrue(kind.steps.length > 0);
            for (TourKind.Step step : kind.steps) {
                assertNotEquals(0, step.titleRes);
                assertNotEquals(0, step.bodyRes);
            }
        }
    }

    @Test
    public void everyTourOpensWithACardNotPointingAtAnything() {
        assertEquals(0, TourKind.HOME.steps[0].targetIds.length);
    }

    @Test
    public void theHomeTourLeadsOnToAddingAnEventAndTheAddTourEndsWithDone() {
        assertEquals(R.string.tour_show_add, TourKind.HOME.lastButtonRes);
        assertEquals(R.string.tutorial_done, TourKind.ADD.lastButtonRes);
    }

    @Test
    public void everyStepWithAControlNamesOneToLightUp() {
        int pointing = 0;
        for (TourKind kind : TourKind.values()) {
            for (TourKind.Step step : kind.steps) {
                for (int id : step.targetIds) {
                    assertNotEquals(0, id);
                    pointing++;
                }
            }
        }
        assertTrue(pointing > 0);
    }
}
