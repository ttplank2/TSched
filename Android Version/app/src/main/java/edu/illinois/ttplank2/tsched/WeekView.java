package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole week at once for wide (landscape) mode: seven day columns from Sunday to Saturday and
 * the hours of the day from top to bottom, labeled every hour like the day view. It is a little taller
 * than the screen, so it sits in a scroll view; the day names are in a fixed {@link WeekHeaderView}
 * above it. Events keep the shapes they have
 * in the day view (solid for fixed, outlined for placed free, dashed for counted, with an overnight
 * event continuing into the next column) but carry no text; tapping one opens its details.
 */
public class WeekView extends View {
    /** The width of the column of hour labels; {@link WeekHeaderView} lines its day names up with it. */
    static final float GUTTER_DP = 46f;
    /** How tall each hour is: roomy enough that the hour labels and events don't feel crowded. */
    private static final float HOUR_HEIGHT_DP = 28f;
    /** A little room under the last hour, so the 11 PM row isn't flush with the bottom. */
    private static final float BOTTOM_PADDING_DP = 8f;
    private static final float BLOCK_GAP_DP = 1f;
    private static final float CORNER_DP = 3f;
    private static final float OUTLINE_DP = 1.5f;
    /** The shortest a block is drawn, so even a 15 minute event can be seen and tapped. */
    private static final float MIN_BLOCK_DP = 4f;
    private static final String[] HOUR_LABELS = TimeFormat.hourLabels();

    private final float density;
    private final Paint gridPaint = new Paint();
    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint hourPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final int dimmedFill;
    private final GestureDetector gestures;

    private List<Event> events = new ArrayList<>();
    /** Only events of this color keep their color; others are grayed. CLEAR means no filter. */
    private int filterColor = ColorWheelDialog.CLEAR;
    private int todayIndex = -1;
    private int nowMinute = Event.NO_TIME;
    private DayScheduleView.BlockListener listener;

    private final List<DayLayout.Placement> placements = new ArrayList<>();
    private final List<RectF> blockBounds = new ArrayList<>();
    private Event tappedEvent;
    /** Index of the block under the user's finger, drawn with the white press flash; -1 if none. */
    private int pressedBlock = -1;

    public WeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        gridPaint.setColor(ContextCompat.getColor(context, R.color.charcoal_line));
        gridPaint.setStrokeWidth(density);
        nowPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        nowPaint.setStrokeWidth(1.5f * density);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(OUTLINE_DP * density);
        dashedPaint.setStyle(Paint.Style.STROKE);
        dashedPaint.setStrokeWidth(OUTLINE_DP * density);
        dashedPaint.setPathEffect(new DashPathEffect(new float[] {4 * density, 3 * density}, 0));
        flashPaint.setColor(ContextCompat.getColor(context, R.color.white_flash));
        dimmedFill = ContextCompat.getColor(context, R.color.filtered_gray);

        // Canvas text doesn't follow the theme, so the app font is set on each paint here.
        Typeface boldFont = Typeface.create(
                ResourcesCompat.getFont(context, R.font.app_font), Typeface.BOLD);
        hourPaint.setTypeface(boldFont);
        hourPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        hourPaint.setTextSize(sp(10));

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                pressedBlock = blockIndexAt(e.getX(), e.getY());
                invalidate();
                return pressedBlock >= 0;
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                tappedEvent = blockAt(e.getX(), e.getY());
                return tappedEvent != null;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                Event event = blockAt(e.getX(), e.getY());
                if (event != null && listener != null) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    listener.onBlockHeld(event);
                }
            }
        });
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    public void setBlockListener(DayScheduleView.BlockListener listener) {
        this.listener = listener;
    }

    /** Shows {@code newEvents}, graying every event whose color isn't {@code filterColor}. */
    public void setWeek(List<Event> newEvents, int filterColor) {
        this.events = newEvents;
        this.filterColor = filterColor;
        LocalTime now = LocalTime.now();
        nowMinute = now.getHour() * 60 + now.getMinute();
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        todayIndex = -1;
        for (int i = 0; i < WeekDays.SUNDAY_FIRST.length; i++) {
            if (WeekDays.SUNDAY_FIRST[i] == today) {
                todayIndex = i;
            }
        }
        setContentDescription(getResources().getQuantityString(
                R.plurals.week_view_description, newEvents.size(), newEvents.size()));
        layoutBlocks();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        layoutBlocks();
    }

    private float gutter() {
        return GUTTER_DP * density;
    }

    private float dayWidth() {
        return (getWidth() - gutter()) / WeekDays.SUNDAY_FIRST.length;
    }

    /** Midnight is a few pixels down, so the 12 AM label on its line has room above it. */
    private float yFor(int minute) {
        return getResources().getDimension(R.dimen.week_top_padding) + minute / 60f * HOUR_HEIGHT_DP * density;
    }

    /** The week is as tall as its hours, whatever room the screen has. */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = (int) (yFor(24 * 60) + BOTTOM_PADDING_DP * density);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    }

    /** Works out each block's rectangle, so drawing allocates nothing. */
    private void layoutBlocks() {
        placements.clear();
        blockBounds.clear();
        if (getWidth() == 0) {
            return;
        }
        float gap = BLOCK_GAP_DP * density;
        float minHeight = MIN_BLOCK_DP * density;
        for (int day = 0; day < WeekDays.SUNDAY_FIRST.length; day++) {
            List<DaySegment> segments = DaySegment.forDay(events, WeekDays.SUNDAY_FIRST[day]);
            float columnArea = dayWidth();
            for (DayLayout.Placement placement : DayLayout.placeSegments(segments)) {
                float columnWidth = columnArea / placement.getColumnCount();
                float left = gutter() + day * columnArea + placement.column * columnWidth + gap;
                float top = yFor(placement.startMinute) + gap;
                float bottom = Math.max(top + minHeight, yFor(placement.endMinute) - gap);
                placements.add(placement);
                blockBounds.add(new RectF(left, top, left + columnWidth - 2 * gap, bottom));
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float gutter = gutter();
        float width = getWidth();
        float dayWidth = dayWidth();
        // The column lines run from the first 12 AM line to the closing one, not into the padding.
        float top = yFor(0);
        float bottom = yFor(24 * 60);
        for (int day = 0; day < WeekDays.SUNDAY_FIRST.length; day++) {
            float x = gutter + day * dayWidth;
            canvas.drawLine(x, top, x, bottom, gridPaint);
        }
        canvas.drawLine(width - 1, top, width - 1, bottom, gridPaint);
        for (int hour = 0; hour <= 24; hour++) {
            float y = yFor(hour * 60);
            canvas.drawLine(gutter, y, width, y, gridPaint);
            // Every hour's label sits on its line; the closing line at the bottom has none.
            if (hour < 24) {
                canvas.drawText(HOUR_LABELS[hour], 6 * density, y + hourPaint.getTextSize() / 3, hourPaint);
            }
        }

        float corner = CORNER_DP * density;
        for (int i = 0; i < blockBounds.size(); i++) {
            RectF block = blockBounds.get(i);
            Event event = placements.get(i).event;
            boolean dimmed = filterColor != ColorWheelDialog.CLEAR && event.getColor() != filterColor;
            int color = dimmed ? dimmedFill : event.getColor();
            if (event.getKind() == Event.Kind.COUNTED) {
                dashedPaint.setColor(color);
                canvas.drawRoundRect(block, corner, corner, dashedPaint);
            } else if (event.isPlaced()) {
                outlinePaint.setColor(color);
                canvas.drawRoundRect(block, corner, corner, outlinePaint);
            } else {
                blockPaint.setColor(color);
                canvas.drawRoundRect(block, corner, corner, blockPaint);
            }
            if (i == pressedBlock) {
                canvas.drawRoundRect(block, corner, corner, flashPaint);
            }
        }

        if (todayIndex >= 0 && nowMinute != Event.NO_TIME) {
            float y = yFor(nowMinute);
            canvas.drawLine(gutter + todayIndex * dayWidth, y, gutter + (todayIndex + 1) * dayWidth, y, nowPaint);
        }
    }

    private Event blockAt(float x, float y) {
        int index = blockIndexAt(x, y);
        return index < 0 ? null : placements.get(index).event;
    }

    private int blockIndexAt(float x, float y) {
        for (int i = blockBounds.size() - 1; i >= 0; i--) {
            if (blockBounds.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = gestures.onTouchEvent(event);
        int action = event.getActionMasked();
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && pressedBlock >= 0) {
            pressedBlock = -1;
            invalidate();
        }
        if (event.getAction() == MotionEvent.ACTION_UP && tappedEvent != null) {
            performClick();
        }
        return handled || super.onTouchEvent(event);
    }

    /** A tap on a block arrives here, so accessibility services can trigger it too. */
    @Override
    public boolean performClick() {
        super.performClick();
        Event event = tappedEvent;
        tappedEvent = null;
        if (event != null && listener != null) {
            Feedback.tick(getContext());
            listener.onBlockTapped(event);
            return true;
        }
        return false;
    }
}
