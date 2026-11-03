package edu.illinois.ttplank2.tsched;

import java.util.ArrayList;
import java.util.List;

/**
 * A hand-off between saving an event and the main calendar: when a newly saved fixed event
 * overlaps placed free events, the form leaves the overlaps here, and the calendar shows its
 * "Free Event Overlap" popup the next time it is on screen. It is taken once, so it appears once.
 */
public final class OverlapNotice {
    private static List<FreeEventOverlaps.Overlap> waiting = new ArrayList<>();

    private OverlapNotice() {
    }

    /** Leaves {@code overlaps} for the calendar to show; an empty list clears any waiting. */
    public static synchronized void leave(List<FreeEventOverlaps.Overlap> overlaps) {
        waiting = new ArrayList<>(overlaps);
    }

    /** The overlaps waiting to be shown, which are then cleared; empty if there are none. */
    public static synchronized List<FreeEventOverlaps.Overlap> take() {
        List<FreeEventOverlaps.Overlap> taken = waiting;
        waiting = new ArrayList<>();
        return taken;
    }
}
