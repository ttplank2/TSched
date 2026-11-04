package edu.illinois.ttplank2.tsched;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.MaterialColors;

/**
 * The base of every screen. A tap on anything pressable ticks when Settings has that on, and every
 * screen turns with the phone to upright or either side, never upside down, even when the phone's
 * own auto-rotate is off. Settings can keep every screen upright.
 */
public abstract class FeedbackActivity extends AppCompatActivity {
    /** Whether Settings says to keep the screens upright. */
    private boolean verticalLocked;
    /**
     * The orientation the phone's sensor last asked for. It starts as whatever the screen is in
     * now, so a screen opened on its side stays on its side until the phone is turned.
     */
    private int sensorOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED;
    private OrientationEventListener orientationSensor;
    /** The accent this screen's theme was built with; a different saved one means a rebuild. */
    private String appliedAccent;
    /** Colors the secret accent choices onto this screen; null for the wheel's own colors. */
    private AccentPalette accentPalette;
    /** Christmas's snow or Halloween's cobwebs, for the whole life of the screen; null for other accents. */
    private View decoration;
    private static final String STATE_TOUR_KIND = "tour_kind";
    private static final String STATE_TOUR_STEP = "tour_step";
    private static final int NO_TOUR = -1;
    /** The guided tour, while it is showing. */
    private GuidedTour tour;
    /** The tour to start again after the screen was rebuilt with a tour open, or NO_TOUR. */
    private int pendingTourKind = NO_TOUR;
    private int pendingTourStep;
    /**
     * Takes the phone's Back button while a tour is showing (it works the tour's own Back), and on
     * a screen that stays put when Back is pressed (it does nothing).
     */
    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (tour != null) {
                tour.onSystemBack();
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // The accent color is part of the theme, so it goes on before anything is drawn.
        appliedAccent = new AccentSetting(this).loadKey();
        getTheme().applyStyle(AccentColors.styleForKey(appliedAccent), true);
        // Before AppCompat installs itself, so it wraps this and every touch passes through it.
        Feedback.watchTaps(getWindow());
        super.onCreate(savedInstanceState);
        readVerticalLock();
        getOnBackPressedDispatcher().addCallback(this, backCallback);
        updateBackCallback();
        if (savedInstanceState != null) {
            pendingTourKind = savedInstanceState.getInt(STATE_TOUR_KIND, NO_TOUR);
            pendingTourStep = savedInstanceState.getInt(STATE_TOUR_STEP);
        }
    }

    /** Starts a guided tour of this screen at {@code startStep}; a tour already going is replaced. */
    protected final void startTour(TourKind kind, int startStep) {
        if (tour != null) {
            tour.dismiss();
        }
        GuidedTour started = new GuidedTour(this, kind, startStep, (finishedKind, completed) -> {
            tour = null;
            updateBackCallback();
            applyOrientation();
            onTourFinished(finishedKind, completed);
        });
        tour = started;
        updateBackCallback();
        // A tour is made for an upright phone, so the screen holds upright until it ends.
        applyOrientation();
        started.start();
    }

    /**
     * Whether the phone's Back button does nothing on this screen, so it can't leave the app. The
     * calendar, the app's first screen, says true; other screens go back as usual.
     */
    protected boolean staysOnBack() {
        return false;
    }

    private void updateBackCallback() {
        backCallback.setEnabled(tour != null || staysOnBack());
    }

    protected final boolean isTourShowing() {
        return tour != null;
    }

    /** Called when a tour ends: {@code completed} is false if the user skipped it. */
    protected void onTourFinished(TourKind kind, boolean completed) {
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TOUR_KIND, tour == null ? NO_TOUR : tour.kind().ordinal());
        outState.putInt(STATE_TOUR_STEP, tour == null ? 0 : tour.step());
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // The screen's own views are in by now, so the decoration goes on top of them.
        decoration = AccentPalette.addDecoration((ViewGroup) getWindow().getDecorView(), appliedAccent,
                hasHalloweenSpider());
    }

    /** Whether Halloween's hanging spider shows on this screen: only the calendar has it. */
    protected boolean hasHalloweenSpider() {
        return false;
    }

    @Override
    protected void onDestroy() {
        if (decoration != null) {
            ((ViewGroup) getWindow().getDecorView()).removeView(decoration);
        }
        if (tour != null) {
            tour.dismiss();
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!new AccentSetting(this).loadKey().equals(appliedAccent)) {
            // Settings changed the accent while this screen waited behind it.
            recreate();
            return;
        }
        accentPalette = AccentPalette.start(getWindow().getDecorView(), appliedAccent,
                MaterialColors.getColor(this, R.attr.appAccent, EventColors.DEFAULT),
                MaterialColors.getColor(this, R.attr.appAccentText, EventColors.DEFAULT),
                MaterialColors.getColor(this, R.attr.appAccentOutline, EventColors.DEFAULT));
        if (pendingTourKind != NO_TOUR) {
            TourKind kind = TourKind.values()[pendingTourKind];
            pendingTourKind = NO_TOUR;
            startTour(kind, pendingTourStep);
        }
        readVerticalLock();
        orientationSensor = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int angle) {
                int orientation = AllowedOrientation.forAngle(angle, sensorOrientation);
                if (orientation != sensorOrientation) {
                    sensorOrientation = orientation;
                    applyOrientation();
                }
            }
        };
        if (orientationSensor.canDetectOrientation()) {
            orientationSensor.enable();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (accentPalette != null) {
            accentPalette.stop();
            accentPalette = null;
        }
        if (orientationSensor != null) {
            orientationSensor.disable();
            orientationSensor = null;
        }
    }

    /** Reads whether Settings says to keep the screens upright, and applies it. */
    private void readVerticalLock() {
        verticalLocked = new VerticalLock(this).isLocked();
        applyOrientation();
    }

    /** The screen stays upright while Settings says so, and for as long as a tour is showing. */
    private void applyOrientation() {
        setRequestedOrientation(verticalLocked || tour != null
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : sensorOrientation);
    }
}
