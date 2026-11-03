package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Little white cobwebs over a screen, for the Halloween accent. Mid-size ones sit in the corners of
 * the visible screen, and smaller ones are strung in the corners of the day cards and of some buttons;
 * those follow their card or button wherever it goes (swiping between days) and are cut off along its
 * rounded outline. Webs on buttons inside a scrolling list are drawn inside the list by
 * {@link ScrollWebs}, so they move and stretch with the buttons. They are only decoration, thin and
 * faint, and they never take a touch. On the calendar screen only, a little spider also hangs from the top on a thread.
 */
final class WebsView extends View {
    private static final float SCREEN_WEB_DP = 58;

    private final WebPainter painter;
    /** The spider that hangs from the top, or null on screens other than the calendar. */
    private final HangingSpider spider;
    private final int[] viewAt = new int[2];
    private final int[] myAt = new int[2];
    private final Rect visibleArea = new Rect();
    private final WebPainter.Locator inWindow = (object, into) -> {
        object.getLocationInWindow(viewAt);
        int left = viewAt[0] - myAt[0];
        int top = viewAt[1] - myAt[1];
        into.set(left, top, left + object.getWidth(), top + object.getHeight());
    };

    WebsView(Context context, boolean withSpider) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        painter = new WebPainter(density);
        spider = withSpider ? new HangingSpider(density) : null;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        getLocationInWindow(myAt);
        // The part of the screen the app can see: inside the status bar at the top and the navigation bar
        // at the bottom, which cover the edges of this view.
        Insets bars = Insets.NONE;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        if (insets != null) {
            bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        }
        visibleArea.set(bars.left, bars.top, getWidth() - bars.right, getHeight() - bars.bottom);
        painter.hang(canvas, 0, visibleArea, 0, false, false, SCREEN_WEB_DP, visibleArea);
        painter.hang(canvas, 1, visibleArea, 0, true, false, SCREEN_WEB_DP * 0.8f, visibleArea);
        painter.hang(canvas, 2, visibleArea, 0, false, true, SCREEN_WEB_DP * 0.65f, visibleArea);
        painter.hang(canvas, 3, visibleArea, 0, true, true, SCREEN_WEB_DP * 0.75f, visibleArea);
        if (getParent() instanceof ViewGroup) {
            painter.hangOnObjects(canvas, (ViewGroup) getParent(), this, inWindow, visibleArea,
                    ScrollWebs::attach);
        }
        if (spider != null) {
            spider.draw(canvas, visibleArea, getWidth(), getHeight());
        }
        postInvalidateOnAnimation();
    }
}
