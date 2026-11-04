package edu.illinois.ttplank2.tsched;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Switches the home-screen icon to the accent's color once the app is out of sight. Doing it while a
 * screen is showing can close the app, so it waits until every screen has stopped.
 */
public final class TSchedApplication extends Application {
    /** How long every screen must stay stopped before the icon is switched, so turning the phone or rebuilding a screen does not count. */
    private static final long OUT_OF_SIGHT_MILLIS = 1500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int startedScreens;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity activity) {
                startedScreens++;
            }

            @Override
            public void onActivityStopped(Activity activity) {
                startedScreens--;
                if (startedScreens == 0 && !LauncherIcon.isCurrent(TSchedApplication.this)) {
                    handler.postDelayed(TSchedApplication.this::switchIconIfOutOfSight, OUT_OF_SIGHT_MILLIS);
                }
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    private void switchIconIfOutOfSight() {
        if (startedScreens == 0) {
            LauncherIcon.apply(this);
        }
    }
}
