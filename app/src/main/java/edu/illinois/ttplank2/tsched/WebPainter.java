package edu.illinois.ttplank2.tsched;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;

import java.util.Random;

/**
 * Draws Halloween's cobwebs: the shapes of the webs, and hanging them in the corners of rectangles,
 * cards and buttons. {@link WebsView} uses it for the whole screen, and {@link ScrollWebs} for what is
 * inside a scrolling list, so the webs move (and stretch) with what they hang on.
 */
final class WebPainter {
    private static final int WEB_ALPHA = 140;
    private static final float THREAD_DP = 1f;
    /** How many different webs there are; each is made once, with its own irregular shape. */
    static final int SHAPES = 8;
    static final float CARD_WEB_DP = 85;
    static final float BUTTON_WEB_DP = 30;
    /** Buttons narrower than this (in dp), such as the weekday buttons, get no web. */
    private static final float MIN_BUTTON_WIDTH_DP = 90;
    /** A web with less than this share of it on the screen is not drawn at all. */
    private static final float MIN_VISIBLE = 0.08f;

    /** Each web in a unit square, anchored at its top left corner, so it can be flipped into any corner. */
    private static final Path[] SHAPE_PATHS = new Path[SHAPES];
    /** The next number to give a card or button that has none yet. */
    private static int nextWebNumber;

    /** Finds where an object is, in the coordinates of what is being drawn on. */
    interface Locator {
        void locate(View object, Rect into);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();
    private final Rect box = new Rect();
    private final float density;

    WebPainter(float density) {
        this.density = density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.WHITE);
    }

    private static synchronized Path shape(int number) {
        if (SHAPE_PATHS[number] == null) {
            SHAPE_PATHS[number] = irregularWeb(new Random(7919L * (number + 1)));
        }
        return SHAPE_PATHS[number];
    }

    /**
     * A web that fills the quarter circle of radius 1 around (0, 0), hung between the two walls of a
     * corner, its rings ending on the walls. Nothing about it is even: its strands are unevenly spaced
     * and of different lengths, the rings between them are at uneven distances, sag by different amounts
     * and sometimes break, and one or two loose threads trail off the ends.
     */
    static Path irregularWeb(Random random) {
        Path web = new Path();
        int strands = 5 + random.nextInt(3);
        double[] angle = new double[strands];
        float[] length = new float[strands];
        for (int i = 0; i < strands; i++) {
            // The first and last strands run right along the two walls, at exactly 0 and 90 degrees.
            double even = 90.0 * i / (strands - 1);
            double wobble = (i == 0 || i == strands - 1) ? 0 : (random.nextDouble() - 0.5) * 12;
            angle[i] = Math.toRadians(even + wobble);
            length[i] = 0.82f + random.nextFloat() * 0.18f;
            if (i == 0 || i == strands - 1) {
                // Drawn along a wall they would only show as a straight line beside it, so the rings
                // simply end on the wall instead.
                length[i] = 1f;
                continue;
            }
            web.moveTo(0, 0);
            web.lineTo((float) Math.cos(angle[i]) * length[i], (float) Math.sin(angle[i]) * length[i]);
        }
        int rings = 4 + random.nextInt(3);
        float reach = 0;
        for (int ring = 1; ring <= rings; ring++) {
            reach += (1f / rings) * (0.8f + random.nextFloat() * 0.4f);
            float distance = Math.min(reach, 1f);
            for (int gap = 0; gap < strands - 1; gap++) {
                if (ring > 2 && random.nextFloat() < 0.12f) {
                    continue;
                }
                float fromX = (float) Math.cos(angle[gap]) * distance * length[gap];
                float fromY = (float) Math.sin(angle[gap]) * distance * length[gap];
                float toX = (float) Math.cos(angle[gap + 1]) * distance * length[gap + 1];
                float toY = (float) Math.sin(angle[gap + 1]) * distance * length[gap + 1];
                float sag = 0.14f + random.nextFloat() * 0.22f;
                float middleX = (fromX + toX) / 2;
                float middleY = (fromY + toY) / 2;
                web.moveTo(fromX, fromY);
                web.quadTo(middleX - middleX * sag, middleY - middleY * sag, toX, toY);
            }
        }
        for (int loose = 0; loose < 2; loose++) {
            int strand = 1 + random.nextInt(strands - 2);
            float endX = (float) Math.cos(angle[strand]) * length[strand];
            float endY = (float) Math.sin(angle[strand]) * length[strand];
            web.moveTo(endX, endY);
            web.quadTo(endX * 1.08f + 0.03f, endY * 1.08f, endX * 1.16f, endY * 1.16f + 0.05f);
        }
        return web;
    }

    /**
     * Draws web number {@code number} in a corner of {@code area} ({@code right} and {@code bottom} say
     * which one), {@code sizeDp} wide. It is cut off along the outline of {@code area}, whose corners are
     * rounded by {@code cornerRadius}, so it never pokes out of a rounded corner. If {@code visibleArea}
     * is given, it also fades out as it goes off that part of the screen, so none of it shows past the edge.
     */
    void hang(Canvas canvas, int number, Rect area, float cornerRadius, boolean right, boolean bottom,
              float sizeDp, Rect visibleArea) {
        float size = sizeDp * density;
        float visible = visibleArea == null ? 1f : onScreen(area, right, bottom, size, visibleArea);
        if (visible < MIN_VISIBLE) {
            return;
        }
        canvas.save();
        if (visibleArea != null) {
            canvas.clipRect(visibleArea);
        }
        outline.reset();
        outline.addRoundRect(new RectF(area), cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.clipPath(outline);
        canvas.translate(right ? area.right : area.left, bottom ? area.bottom : area.top);
        canvas.scale(right ? -size : size, bottom ? -size : size);
        paint.setStrokeWidth(THREAD_DP * density / size);
        paint.setAlpha(Math.round(WEB_ALPHA * (visible >= 0.999f ? 1f : (float) Math.sqrt(visible))));
        canvas.drawPath(shape(number), paint);
        canvas.restore();
    }

    /** How much of the square the web covers, from 0 to 1, is within {@code visibleArea}. */
    private static float onScreen(Rect area, boolean right, boolean bottom, float size, Rect visibleArea) {
        float left = right ? area.right - size : area.left;
        float top = bottom ? area.bottom - size : area.top;
        float inWidth = Math.min(left + size, visibleArea.right) - Math.max(left, visibleArea.left);
        float inHeight = Math.min(top + size, visibleArea.bottom) - Math.max(top, visibleArea.top);
        if (inWidth <= 0 || inHeight <= 0) {
            return 0;
        }
        return inWidth * inHeight / (size * size);
    }

    /**
     * The number that decides which web (and which corner) a card or button gets. It is given the first
     * time the view is seen and kept, so a view never changes its web as the screen changes around it,
     * such as when swiping makes the pages shuffle their order.
     */
    private static synchronized int webNumberOf(View object) {
        Object known = object.getTag(R.id.web_number);
        if (known != null) {
            return (Integer) known;
        }
        int number = nextWebNumber++;
        object.setTag(R.id.web_number, number);
        return number;
    }

    /** How much {@code object}'s corners are rounded, in pixels. */
    private static float cornerOf(View object) {
        float limit = Math.min(object.getWidth(), object.getHeight()) / 2f;
        if (object instanceof CardView) {
            return Math.min(((CardView) object).getRadius(), limit);
        }
        if (object instanceof MaterialButton) {
            int radius = ((MaterialButton) object).getCornerRadius();
            return radius > 0 ? Math.min(radius, limit) : limit;
        }
        return 0;
    }

    /**
     * Hangs webs on the day cards and buttons under {@code group} (but not {@code skip}), wherever
     * {@code locator} says they are. Scrolling lists are not entered: {@code onScrollView} is called for
     * each instead, because what is inside them must be drawn inside them to move with them.
     */
    void hangOnObjects(Canvas canvas, ViewGroup group, View skip, Locator locator, Rect visibleArea,
                       ScrollHandler onScrollView) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == skip || !child.isShown()) {
                continue;
            }
            if (child instanceof ScrollView && onScrollView != null) {
                onScrollView.found((ScrollView) child);
                continue;
            }
            if (child instanceof CardView) {
                hangOn(canvas, child, 4 + webNumberOf(child) % 4, true, false, CARD_WEB_DP, locator, visibleArea);
            } else if (child instanceof MaterialButton && child.getWidth() >= MIN_BUTTON_WIDTH_DP * density) {
                int number = webNumberOf(child);
                // Two buttons in three get one, in a corner that changes from button to button.
                if (number % 3 == 0) {
                    hangOn(canvas, child, number % SHAPES, false, false, BUTTON_WEB_DP, locator, visibleArea);
                } else if (number % 3 == 2) {
                    hangOn(canvas, child, number % SHAPES, true, true, BUTTON_WEB_DP, locator, visibleArea);
                }
            }
            if (child instanceof ViewGroup) {
                hangOnObjects(canvas, (ViewGroup) child, skip, locator, visibleArea, onScrollView);
            }
        }
    }

    /** Told about each scrolling list the painter comes across. */
    interface ScrollHandler {
        void found(ScrollView scroll);
    }

    private void hangOn(Canvas canvas, View object, int number, boolean right, boolean bottom, float sizeDp,
                        Locator locator, Rect visibleArea) {
        locator.locate(object, box);
        hang(canvas, number, box, cornerOf(object), right, bottom, sizeDp, visibleArea);
    }
}
