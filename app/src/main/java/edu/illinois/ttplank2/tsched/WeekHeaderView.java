package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import androidx.core.content.res.ResourcesCompat;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * The row of day names over the top of the wide-mode week, always in view while the week scrolls
 * beneath it. It covers only the day columns; the hour labels beside it stay visible. Every name is yellow, and today's reads "Tues · Today" like the title of the day view.
 */
public class WeekHeaderView extends View {
    private final float density;
    private final Paint linePaint = new Paint();
    private final Paint backgroundPaint = new Paint();
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final String[] labels = new String[WeekDays.SUNDAY_FIRST.length];
    private int todayIndex = -1;

    public WeekHeaderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        linePaint.setColor(ContextCompat.getColor(context, R.color.charcoal_line));
        linePaint.setStrokeWidth(density);
        backgroundPaint.setColor(ContextCompat.getColor(context, R.color.charcoal));
        Typeface boldFont = Typeface.create(
                ResourcesCompat.getFont(context, R.font.app_font), Typeface.BOLD);
        namePaint.setTypeface(boldFont);
        namePaint.setColor(MaterialColors.getColor(context, R.attr.appAccentText, EventColors.DEFAULT));
        namePaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
        namePaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < labels.length; i++) {
            labels[i] = WeekDays.shortName(context, WeekDays.SUNDAY_FIRST[i]);
        }
        showToday();
    }

    /** Marks today's column; call again when the day may have changed. */
    public void showToday() {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        todayIndex = -1;
        for (int i = 0; i < labels.length; i++) {
            if (WeekDays.SUNDAY_FIRST[i] == today) {
                todayIndex = i;
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float gutter = WeekView.GUTTER_DP * density;
        float dayWidth = (getWidth() - gutter) / labels.length;
        // The bar covers only the day columns, so the hour labels beside it are never hidden.
        canvas.drawRect(gutter, 0, getWidth(), getHeight(), backgroundPaint);
        float baseline = getHeight() / 2f + namePaint.getTextSize() / 3f;
        for (int day = 0; day < labels.length; day++) {
            String label = day == todayIndex
                    ? getContext().getString(R.string.today_title, labels[day]) : labels[day];
            canvas.drawText(label, gutter + (day + 0.5f) * dayWidth, baseline, namePaint);
            float x = gutter + day * dayWidth;
            canvas.drawLine(x, 0, x, getHeight(), linePaint);
        }
        canvas.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight(), linePaint);
        // The line under the day names stops at the first column: none over the hour labels.
        canvas.drawLine(gutter, getHeight() - 1, getWidth(), getHeight() - 1, linePaint);
    }
}
