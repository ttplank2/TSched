package edu.illinois.ttplank2.tsched;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.annotation.NonNull;

/**
 * The cobwebs on the buttons inside a scrolling list. They are drawn as part of the list's own content
 * (as an overlay of it), not over the screen, so they scroll with the buttons and also move with them when
 * the list stretches at its end, which only moves what is drawn and not where the buttons are.
 */
final class ScrollWebs extends Drawable {
    private final ViewGroup content;
    private final WebPainter painter;
    private final WebPainter.Locator inContent = this::locate;

    private ScrollWebs(ViewGroup content) {
        this.content = content;
        painter = new WebPainter(content.getResources().getDisplayMetrics().density);
    }

    /** Starts hanging webs on the buttons inside {@code scroll}, if that isn't already done. */
    static void attach(ScrollView scroll) {
        if (scroll.getTag(R.id.scroll_webs) != null || !(scroll.getChildAt(0) instanceof ViewGroup)) {
            return;
        }
        ViewGroup content = (ViewGroup) scroll.getChildAt(0);
        ScrollWebs webs = new ScrollWebs(content);
        scroll.setTag(R.id.scroll_webs, webs);
        content.getOverlay().add(webs);
        webs.setBounds(0, 0, content.getWidth(), content.getHeight());
        content.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight,
                                           oldBottom) -> {
            webs.setBounds(0, 0, right - left, bottom - top);
            webs.invalidateSelf();
        });
    }

    /** Where {@code object} is in the list's content: its place within each view it is inside. */
    private void locate(View object, Rect into) {
        int left = 0;
        int top = 0;
        for (View view = object; view != content && view != null; view = (View) view.getParent()) {
            left += view.getLeft() + Math.round(view.getTranslationX());
            top += view.getTop() + Math.round(view.getTranslationY());
        }
        into.set(left, top, left + object.getWidth(), top + object.getHeight());
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        painter.hangOnObjects(canvas, content, null, inContent, null, null);
    }

    @Override
    public void setAlpha(int alpha) {
        // The webs set their own faintness.
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // The webs are always white.
    }

    // Drawable still requires this method on the oldest supported Android versions.
    @SuppressWarnings("deprecation")
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
