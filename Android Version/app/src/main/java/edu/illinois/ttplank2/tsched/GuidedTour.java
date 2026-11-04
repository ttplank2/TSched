package edu.illinois.ttplank2.tsched;

import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

/**
 * Runs one {@link TourKind} on a screen: lights up each step's real control in turn, scrolling it
 * into view if it needs to, and moves on, back, or out with the card's buttons.
 */
final class GuidedTour {
    /** A control may not be on screen the instant the tour starts, so it is looked for a few times. */
    private static final int MAX_LOOKS = 20;
    private static final long LOOK_AGAIN_MILLIS = 60;

    /** Told when the tour ends. */
    interface Listener {
        /** {@code completed} is true if the user went through to the end, false if they skipped. */
        void onFinished(TourKind kind, boolean completed);
    }

    private final FeedbackActivity activity;
    private final TourKind kind;
    private final TutorialPager pager;
    private final Listener listener;
    private final TourOverlay overlay;
    /** Which way the user is going, so a step whose control isn't showing is passed over that way. */
    private int direction = 1;
    private boolean ended;

    GuidedTour(FeedbackActivity activity, TourKind kind, int startStep, Listener listener) {
        this.activity = activity;
        this.kind = kind;
        this.pager = new TutorialPager(kind.steps.length, startStep);
        this.listener = listener;
        this.overlay = new TourOverlay(activity);
        overlay.setButtonListeners(
                view -> finish(false),
                view -> go(-1),
                view -> {
                    if (pager.isLast()) {
                        finish(true);
                    } else {
                        go(1);
                    }
                });
    }

    /** Covers the screen and shows the first step. */
    void start() {
        ((ViewGroup) activity.getWindow().getDecorView()).addView(overlay,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        showStep(0);
    }

    TourKind kind() {
        return kind;
    }

    /** The step showing now, so the tour can start there again after the screen turns. */
    int step() {
        return pager.index();
    }

    /** Takes the cover off without telling the listener, such as when the screen is closing. */
    void dismiss() {
        ended = true;
        ((ViewGroup) activity.getWindow().getDecorView()).removeView(overlay);
    }

    /**
     * The phone's Back button: it goes back a step like the card's Back, and on the first step it
     * leaves the tour like Skip.
     */
    void onSystemBack() {
        if (pager.isFirst()) {
            finish(false);
        } else {
            go(-1);
        }
    }

    private void go(int by) {
        direction = by;
        if (by > 0) {
            pager.next();
        } else {
            pager.back();
        }
        showStep(0);
    }

    private void finish(boolean completed) {
        if (ended) {
            return;
        }
        dismiss();
        listener.onFinished(kind, completed);
    }

    private void showStep(int looks) {
        if (ended) {
            return;
        }
        TourKind.Step step = kind.steps[pager.index()];
        RectF target = null;
        if (step.targetIds.length > 0) {
            View control = findControl(step);
            if (control == null) {
                if (looks < MAX_LOOKS) {
                    overlay.postDelayed(() -> showStep(looks + 1), LOOK_AGAIN_MILLIS);
                } else {
                    passOver();
                }
                return;
            }
            target = boundsOf(control);
        }
        overlay.showStep(target,
                activity.getString(R.string.tutorial_counter, pager.index() + 1, pager.count()),
                activity.getString(step.titleRes), activity.getString(step.bodyRes),
                pager.isFirst(), pager.isLast(),
                pager.isLast() ? kind.lastButtonRes : R.string.tutorial_next);
    }

    /** The first of the step's controls that is showing, or null if none is. */
    private View findControl(TourKind.Step step) {
        for (int id : step.targetIds) {
            View control = activity.findViewById(id);
            if (control != null && control.isShown() && control.getWidth() > 0 && control.getHeight() > 0) {
                return control;
            }
        }
        return null;
    }

    /** The control's place on the cover, after scrolling it into view if it was out of sight. */
    private RectF boundsOf(View control) {
        control.requestRectangleOnScreen(new Rect(0, 0, control.getWidth(), control.getHeight()), true);
        int[] onScreen = new int[2];
        int[] coverOnScreen = new int[2];
        control.getLocationOnScreen(onScreen);
        overlay.getLocationOnScreen(coverOnScreen);
        float left = onScreen[0] - coverOnScreen[0];
        float top = onScreen[1] - coverOnScreen[1];
        return new RectF(left, top, left + control.getWidth(), top + control.getHeight());
    }

    /** A step whose control never showed (such as Snap to Day when the phone is on its side). */
    private void passOver() {
        if (direction > 0 && !pager.isLast()) {
            pager.next();
            showStep(0);
        } else if (direction < 0 && !pager.isFirst()) {
            pager.back();
            showStep(0);
        } else if (pager.isLast()) {
            finish(true);
        } else {
            pager.next();
            direction = 1;
            showStep(0);
        }
    }
}
