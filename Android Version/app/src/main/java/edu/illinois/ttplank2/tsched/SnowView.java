package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/**
 * A few small white snowflakes drifting down over a screen, for the Christmas accent. It is only
 * decoration: low detail (plain dots), few of them, and it never takes a touch.
 *
 * <p>Where every flake is depends only on the app-wide time, never on this view, so each screen
 * draws the same snow at the same moment and the flakes carry on across screens.
 */
final class SnowView extends View {
    private static final int FLAKES = 14;
    /** Flake sizes, in dp (the radius), and how fast they fall, in dp per second. */
    private static final float MIN_RADIUS = 1.5f;
    private static final float MAX_RADIUS = 3f;
    private static final float MIN_SPEED = 22f;
    private static final float MAX_SPEED = 48f;
    /** How far a flake sways to each side, in dp, and how long one sway takes, in seconds. */
    private static final float SWAY = 10f;
    private static final float SWAY_SECONDS = 5f;

    /** When the snow began, shared by every screen; not set until a screen first draws it. */
    private static long origin = -1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    SnowView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        paint.setColor(Color.WHITE);
        paint.setAlpha(230);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** A steady pseudo-random number from 0 up to 1 for these two whole numbers. */
    static float unit(int a, int b) {
        double hashed = Math.sin(a * 12.9898 + b * 78.233) * 43758.5453;
        return (float) (hashed - Math.floor(hashed));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float heightDp = getHeight() / density;
        float margin = MAX_RADIUS * 2;
        float travel = heightDp + 2 * margin;
        if (origin < 0) {
            origin = SystemClock.elapsedRealtime();
        }
        float seconds = (SystemClock.elapsedRealtime() - origin) / 1000f;
        for (int i = 0; i < FLAKES; i++) {
            float radius = MIN_RADIUS + unit(i, 1) * (MAX_RADIUS - MIN_RADIUS);
            float speed = MIN_SPEED + unit(i, 2) * (MAX_SPEED - MIN_SPEED);
            float phase = unit(i, 3) * (float) (2 * Math.PI);
            float fallen = speed * seconds + unit(i, 4) * travel;
            int pass = (int) Math.floor(fallen / travel);
            float down = fallen - pass * travel - margin;
            // Each pass comes down a different part of the screen.
            float across = unit(i, 100 + pass);
            double sway = Math.sin(seconds * 2 * Math.PI / SWAY_SECONDS + phase) * SWAY;
            canvas.drawCircle(across * getWidth() + (float) sway * density, down * density,
                    radius * density, paint);
        }
        postInvalidateOnAnimation();
    }
}
