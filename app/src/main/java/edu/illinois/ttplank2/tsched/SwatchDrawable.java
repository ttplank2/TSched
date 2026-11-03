package edu.illinois.ttplank2.tsched;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/** A rounded swatch cut into equal diagonal stripes (at 45 degrees) of the given colors, with an outline. */
final class SwatchDrawable extends Drawable {
    private final int[] colors;
    private final float corner;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF bounds = new RectF();

    SwatchDrawable(int[] colors, float corner, float strokeWidth, int strokeColor) {
        this.colors = colors;
        this.corner = corner;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(strokeWidth);
        outline.setColor(strokeColor);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        bounds.set(getBounds());
        shape.reset();
        shape.addRoundRect(bounds, corner, corner, Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(shape);
        // Equal bands across the diagonal, cut at 45 degrees: turn the canvas, then draw plain stripes
        // wide enough to cover the whole swatch.
        float reach = (float) Math.hypot(bounds.width(), bounds.height());
        float stripe = reach / colors.length;
        canvas.rotate(45, bounds.centerX(), bounds.centerY());
        for (int i = 0; i < colors.length; i++) {
            fill.setColor(colors[i]);
            float left = bounds.centerX() - reach / 2 + i * stripe;
            canvas.drawRect(left, bounds.centerY() - reach / 2,
                    left + stripe + 1, bounds.centerY() + reach / 2, fill);
        }
        canvas.restoreToCount(saved);
        canvas.drawRoundRect(bounds, corner, corner, outline);
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        outline.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        outline.setColorFilter(colorFilter);
    }

    // Drawable still requires this method on the oldest supported Android versions.
    @SuppressWarnings("deprecation")
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
