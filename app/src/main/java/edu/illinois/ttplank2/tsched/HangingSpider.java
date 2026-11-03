package edu.illinois.ttplank2.tsched;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.SystemClock;

/**
 * A little round, cute (chibi) spider that hangs from the top of the screen on a thread that goes straight
 * up, for Halloween. It lowers itself and climbs back at one slow steady speed, stopping now and then, within
 * the top third of the screen (the top half when the screen is wide). It does not move in any other way.
 * Where it is depends only on the app-wide time, so every screen shows it in the same place and it carries
 * on across screen changes. It is only decoration.
 */
final class HangingSpider {
    /** How big its head is (the radius, in dp). Everything else is drawn in proportion to it. */
    static final float HEAD_DP = 6.5f;
    /** How far across the screen the spider hangs, as a share of the width: between the logo and the middle. */
    private static final float ACROSS = 0.375f;
    /** How fast it lowers or climbs, in dp per second: always this fast while it moves. */
    static final float SPEED_DP_PER_SECOND = 14f;
    /**
     * Where it goes, in order, then starts again from the top: how far down its zone each stop is (0 at the
     * top, 1 at the bottom of the zone), and how long it stays still there (seconds).
     */
    static final float[] STOP_DEPTH = {0.5f, 1f, 0.2f, 0f};
    static final float[] STOP_SECONDS = {3f, 4f, 2f, 3f};
    /** The first leg's angle below sideways (negative is above it), so that the middle of each side points straight out, and the same angle between each leg and the next (degrees), and how far the legs reach (head radii). */
    private static final float LEG_FIRST_DEGREES = -45f;
    private static final float LEG_STEP_DEGREES = 30f;
    private static final float LEG_REACH = 1.7f;
    /** How long the part of a leg past its knee is (head radii), and how far it bends from the part before, up for the upper legs and down for the lower (degrees). */
    private static final float LEG_LOWER = 0.9f;
    private static final float LEG_BEND_DEGREES = 40f;
    /** The head's radius, in the same units (a smaller head than the back, as on a real spider). */
    private static final float HEAD_RADIUS = 0.7f;
    /** The back is this big (head radii) and its center this far up from the head's center. */
    private static final float BACK_RADIUS = 0.95f;
    private static final float BACK_CENTER = 1.6f;
    private static final int THREAD_ALPHA = 150;
    /** The eyes are the Halloween accent orange. */
    private static final int EYE_COLOR = 0xFFFF8A3D;

    /** When the spider began, shared by every screen; not set until a screen first draws it. */
    private static long origin = -1;

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legs = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thread = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path leg = new Path();
    private final float density;

    HangingSpider(float density) {
        this.density = density;
        body.setStyle(Paint.Style.FILL);
        body.setColor(Color.BLACK);
        legs.setStyle(Paint.Style.STROKE);
        legs.setStrokeCap(Paint.Cap.ROUND);
        legs.setStrokeJoin(Paint.Join.ROUND);
        legs.setColor(Color.BLACK);
        legs.setStrokeWidth(1.8f * density);
        thread.setStyle(Paint.Style.STROKE);
        thread.setColor(Color.WHITE);
        thread.setAlpha(THREAD_ALPHA);
        thread.setStrokeWidth(1f * density);
        eye.setStyle(Paint.Style.FILL);
    }

    /** How much of the screen's height the spider may hang down over: a third, or a half when the screen is wide. */
    static float zoneShare(float width, float height) {
        return width > height ? 0.5f : 1f / 3f;
    }

    /**
     * How far down its zone the spider is {@code seconds} after it began, from 0 (at the top) to 1 (at the
     * bottom of the zone), when the zone is {@code reach} pixels tall and the spider moves {@code speed}
     * pixels a second. It goes from stop to stop at that one speed, stays still at each for a while, and
     * starts over after the last.
     */
    static float depthShare(float seconds, float reach, float speed) {
        float cycle = 0;
        float last = 0;
        for (int stop = 0; stop < STOP_DEPTH.length; stop++) {
            cycle += Math.abs(STOP_DEPTH[stop] - last) * reach / speed + STOP_SECONDS[stop];
            last = STOP_DEPTH[stop];
        }
        float time = seconds % cycle;
        float from = 0;
        for (int stop = 0; stop < STOP_DEPTH.length; stop++) {
            float travel = Math.abs(STOP_DEPTH[stop] - from) * reach / speed;
            if (time < travel) {
                return from + (STOP_DEPTH[stop] - from) * (time / travel);
            }
            time -= travel;
            if (time < STOP_SECONDS[stop]) {
                return STOP_DEPTH[stop];
            }
            time -= STOP_SECONDS[stop];
            from = STOP_DEPTH[stop];
        }
        return from;
    }

    /** Draws the spider and its thread, hanging from the top of {@code visible} (the part of the screen that can be seen). */
    void draw(Canvas canvas, Rect visible, float width, float height) {
        if (origin < 0) {
            origin = SystemClock.elapsedRealtime();
        }
        float seconds = (SystemClock.elapsedRealtime() - origin) / 1000f;
        float head = HEAD_DP * density;
        float reach = zoneShare(width, height) * visible.height();
        // The spider is about 5 head radii tall and its legs reach 2 radii out each side; keep it inside the zone.
        float top = visible.top;
        float room = reach - head * 5.4f;
        float centerY = top + head * 3.6f
                + depthShare(seconds, room, SPEED_DP_PER_SECOND * density) * room;
        float centerX = visible.left + ACROSS * visible.width();
        // The thread goes straight up from the spider's back to the top of the screen.
        canvas.drawLine(centerX, top, centerX, centerY - head * (BACK_CENTER + BACK_RADIUS), thread);
        drawSpider(canvas, centerX, centerY, head);
    }

    /**
     * Draws a round spider with its center (the middle of its big head) at (x, y): a big round back behind
     * the small head where the thread is tied, eight tiny eyes, and eight short legs with one bend each, four on each side.
     */
    private void drawSpider(Canvas canvas, float x, float y, float head) {
        canvas.save();
        canvas.translate(x, y);
        // Legs first, so the body covers where they start: eight legs of one length, four on each side, going
        // out from the middle of the whole spider (between the head and the back) with the same angle between
        // each leg and the next, and each bending once at a knee, the upper ones up and the lower ones down.
        float middleY = head * (HEAD_RADIUS - BACK_CENTER - BACK_RADIUS) / 2;
        for (int side = -1; side <= 1; side += 2) {
            for (int number = 0; number < 4; number++) {
                double angle = Math.toRadians(LEG_FIRST_DEGREES + number * LEG_STEP_DEGREES);
                // The upper legs bend up and the lower ones bend down, away from the middle of the side.
                double footAngle = angle + Math.toRadians(angle < 0 ? -LEG_BEND_DEGREES : LEG_BEND_DEGREES);
                float startX = side * (float) Math.cos(angle) * head * 0.7f;
                float startY = middleY + (float) Math.sin(angle) * head * 0.7f;
                float kneeX = side * (float) Math.cos(angle) * head * LEG_REACH;
                float kneeY = middleY + (float) Math.sin(angle) * head * LEG_REACH;
                float footX = kneeX + side * (float) Math.cos(footAngle) * head * LEG_LOWER;
                float footY = kneeY + (float) Math.sin(footAngle) * head * LEG_LOWER;
                leg.reset();
                leg.moveTo(startX, startY);
                leg.lineTo(kneeX, kneeY);
                leg.lineTo(footX, footY);
                canvas.drawPath(leg, legs);
            }
        }
        // The back (small, up where the thread is) and the big round head.
        canvas.drawCircle(0, -head * BACK_CENTER, head * BACK_RADIUS, body);
        canvas.drawCircle(0, 0, head * HEAD_RADIUS, body);
        // Eight tiny orange eyes, as on a real spider: a front row of four, a pair above it and another pair
        // above that, each pair a little smaller than the row before.
        eye.setColor(EYE_COLOR);
        for (int side = -1; side <= 1; side += 2) {
            canvas.drawCircle(side * head * 0.12f, head * 0.2f, head * 0.1f, eye);
            // The outer two of the front row sit a little higher, so the row is not one straight line.
            canvas.drawCircle(side * head * 0.36f, head * 0.1f, head * 0.1f, eye);
            canvas.drawCircle(side * head * 0.22f, -head * 0.08f, head * 0.12f, eye);
            canvas.drawCircle(side * head * 0.38f, -head * 0.3f, head * 0.09f, eye);
        }
        canvas.restore();
    }
}
