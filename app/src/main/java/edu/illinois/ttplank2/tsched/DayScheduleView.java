package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayList;
import java.util.List;

/** One day as an hour grid from midnight to midnight, with fixed events drawn as blocks. */
public class DayScheduleView extends View {
    /** Called when the user taps or presses and holds an event's block. */
    public interface BlockListener {
        void onBlockTapped(Event event);

        void onBlockHeld(Event event);
    }

    private static final int HOURS = 24;
    private static final float HOUR_HEIGHT_DP = 60f;
    private static final float GUTTER_DP = 52f;
    private static final float TOP_PADDING_DP = 10f;
    private static final float BLOCK_GAP_DP = 2f;
    private static final float CORNER_DP = 6f;
    private static final float TEXT_PADDING_DP = 5f;
    /**
     * Space below each name line and below the time line inside a block. Kept tight so a
     * one-hour block fits two name lines plus the time.
     */
    private static final float TITLE_LINE_GAP_DP = 2f;
    private static final float DETAILS_LINE_GAP_DP = 2f;
    private static final String[] HOUR_LABELS = TimeFormat.hourLabels();

    private final float density;
    private final Paint gridPaint = new Paint();
    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The outline of a counted event: dashes like the lines down the middle of a road. */
    private final Paint dashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The small number in the bottom right corner of a counted event, showing its count. */
    private final TextPaint countPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint outlineTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint outlineTimePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The triangle at the start of the "now" line, reused so drawing allocates nothing. */
    private final Path nowArrow = new Path();
    /** The triangle on an overnight event's blocks, reused so drawing allocates nothing. */
    private final Path continuationArrow = new Path();
    /** The gap kept between an overnight triangle and the name or time text beside it. */
    private static final float CONTINUATION_TEXT_GAP_DP = 2f;
    private final TextPaint hourPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final GestureDetector gestures;

    private final List<DayLayout.Placement> placements = new ArrayList<>();
    private final List<RectF> blockBounds = new ArrayList<>();
    /** Each block's name, already wrapped into lines that fit. */
    private final List<List<String>> blockTitles = new ArrayList<>();
    /** Each block's time and location line, or null when there's no room for it. */
    private final List<String> blockDetails = new ArrayList<>();
    /** Each block's count as text, or null for an event that isn't counted. */
    private final List<String> blockCounts = new ArrayList<>();
    /** Where each block's first line of text starts; lower on a block whose top has a triangle. */
    private final List<Float> blockTextTops = new ArrayList<>();
    private Event tappedEvent;
    /** Index of the block under the user's finger, drawn with the white press flash; -1 if none. */
    private int pressedBlock = -1;
    /** Only events of this color keep their color; others are grayed. CLEAR means no filter. */
    private int filterColor = ColorWheelDialog.CLEAR;
    private final int dimmedFill;
    private final int darkText;
    private final int lightText;
    private final int dimmedText;
    private int nowMinute = Event.NO_TIME;
    private BlockListener listener;

    public DayScheduleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        gridPaint.setColor(ContextCompat.getColor(context, R.color.charcoal_line));
        gridPaint.setStrokeWidth(density);
        nowPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        nowPaint.setStrokeWidth(2 * density);
        // Canvas text doesn't follow the theme, so the app font is set on each paint here.
        Typeface font = ResourcesCompat.getFont(context, R.font.app_font);
        Typeface boldFont = Typeface.create(font, Typeface.BOLD);
        hourPaint.setTypeface(boldFont);
        titlePaint.setTypeface(boldFont);
        // Event times are bold on every block, solid or outlined.
        timePaint.setTypeface(boldFont);
        hourPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        hourPaint.setTextSize(sp(11));
        titlePaint.setColor(ContextCompat.getColor(context, R.color.charcoal));
        titlePaint.setTextSize(sp(14));
        timePaint.setColor(ContextCompat.getColor(context, R.color.charcoal));
        timePaint.setTextSize(sp(12));
        countPaint.setTypeface(boldFont);
        countPaint.setTextSize(sp(12));
        countPaint.setTextAlign(Paint.Align.RIGHT);
        // Placed free events are outlined in yellow so they read differently from fixed events.
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(2 * density);
        dashedPaint.setStyle(Paint.Style.STROKE);
        dashedPaint.setStrokeWidth(2 * density);
        dashedPaint.setPathEffect(new DashPathEffect(new float[] {8 * density, 5 * density}, 0));
        outlineTitlePaint.set(titlePaint);
        outlineTitlePaint.setColor(MaterialColors.getColor(context, R.attr.appAccent, EventColors.DEFAULT));
        outlineTimePaint.set(timePaint);
        outlineTimePaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        flashPaint.setColor(ContextCompat.getColor(context, R.color.white_flash));
        dimmedFill = ContextCompat.getColor(context, R.color.filtered_gray);
        darkText = ContextCompat.getColor(context, R.color.charcoal);
        lightText = ContextCompat.getColor(context, R.color.text_primary);
        dimmedText = ContextCompat.getColor(context, R.color.text_secondary);

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

    public void setBlockListener(BlockListener listener) {
        this.listener = listener;
    }

    /**
     * Shows these parts of events, with a "now" line at {@code nowMinute} (or none if NO_TIME), graying
     * every event whose color isn't {@code filterColor} unless it is {@link ColorWheelDialog#CLEAR}.
     */
    public void setDay(List<DaySegment> segments, int nowMinute, int filterColor) {
        this.filterColor = filterColor;
        placements.clear();
        placements.addAll(DayLayout.placeSegments(segments));
        this.nowMinute = nowMinute;
        setContentDescription(describe(segments));
        layoutBlocks();
        requestLayout();
        invalidate();
    }

    /** Pixels from the top of this view to the given time, for scrolling to it. */
    public int offsetOf(int minute) {
        return (int) yFor(minute);
    }

    private float yFor(int minute) {
        return (TOP_PADDING_DP + minute / 60f * HOUR_HEIGHT_DP) * density;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = (int) (yFor(HOURS * 60) + TOP_PADDING_DP * density);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        layoutBlocks();
    }

    /** Works out each block's rectangle and its fitted text, so drawing allocates nothing. */
    private void layoutBlocks() {
        blockBounds.clear();
        blockTitles.clear();
        blockDetails.clear();
        blockCounts.clear();
        blockTextTops.clear();
        float width = getWidth();
        if (width == 0) {
            return;
        }
        float gutter = GUTTER_DP * density;
        float gap = BLOCK_GAP_DP * density;
        float columnArea = width - gutter - 4 * density;
        for (DayLayout.Placement placement : placements) {
            Event event = placement.event;
            float columnWidth = columnArea / placement.getColumnCount();
            RectF block = new RectF(
                    gutter + placement.column * columnWidth + gap,
                    yFor(placement.startMinute) + gap,
                    gutter + (placement.column + 1) * columnWidth - gap,
                    yFor(placement.endMinute) - gap);
            float padding = TEXT_PADDING_DP * density;
            float textWidth = Math.max(0, block.width() - 2 * padding);
            // A triangle on the top or bottom edge takes room from the text, so they never touch.
            float triangleRoom = placement.continuesAfter || placement.continuesFrom
                    ? Math.max(0, continuationHeight() + CONTINUATION_TEXT_GAP_DP * density - padding)
                    : 0;
            float topInset = placement.continuesFrom ? triangleRoom : 0;
            float bottomInset = placement.continuesAfter ? triangleRoom : 0;
            float textHeight = block.height() - 2 * padding - topInset - bottomInset;
            float titleLine = titleLineHeight();
            float detailsLine = detailsLineHeight();

            BlockText.Fit fit = BlockText.fit(placement.endMinute - placement.startMinute, textHeight, titleLine, detailsLine);
            List<String> title = fit.nameLines == 0 ? List.of()
                    : BlockText.wrap(event.getName(), textWidth, fit.nameLines, titlePaint::measureText);
            String details = TimeFormat.range(event.getStartMinute(), event.getEndMinute());
            if (!event.getLocation().isEmpty()) {
                details += " · " + event.getLocation();
            }

            blockBounds.add(block);
            blockTextTops.add(block.top + padding + topInset);
            blockTitles.add(title);
            blockDetails.add(fit.showTime ? ellipsize(details, timePaint, textWidth) : null);
            blockCounts.add(event.getKind() == Event.Kind.COUNTED ? String.valueOf(event.getCount()) : null);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float gutter = GUTTER_DP * density;
        float width = getWidth();
        for (int hour = 0; hour <= HOURS; hour++) {
            float y = yFor(hour * 60);
            canvas.drawLine(gutter, y, width, y, gridPaint);
            if (hour < HOURS) {
                canvas.drawText(HOUR_LABELS[hour], 6 * density, y + hourPaint.getTextSize() / 3, hourPaint);
            }
        }

        float corner = CORNER_DP * density;
        float padding = TEXT_PADDING_DP * density;
        for (int i = 0; i < blockBounds.size(); i++) {
            RectF block = blockBounds.get(i);
            Event event = placements.get(i).event;
            boolean counted = event.getKind() == Event.Kind.COUNTED;
            // Placed free events and counted events are outlines; a counted one is dashed.
            boolean outlined = event.isPlaced() || counted;
            boolean dimmed = filterColor != ColorWheelDialog.CLEAR && event.getColor() != filterColor;
            int color = dimmed ? dimmedFill : event.getColor();
            blockPaint.setColor(color);
            outlinePaint.setColor(color);
            dashedPaint.setColor(color);
            canvas.drawRoundRect(block, corner, corner,
                    counted ? dashedPaint : outlined ? outlinePaint : blockPaint);
            if (i == pressedBlock) {
                canvas.drawRoundRect(block, corner, corner, flashPaint);
            }
            TextPaint title = outlined ? outlineTitlePaint : titlePaint;
            TextPaint time = outlined ? outlineTimePaint : timePaint;
            if (dimmed) {
                title.setColor(dimmedText);
                time.setColor(dimmedText);
            } else if (outlined) {
                title.setColor(color);
                time.setColor(lightText);
            } else {
                // Text on a solid colored block is always black, whatever the color.
                title.setColor(darkText);
                time.setColor(darkText);
            }
            float baseline = blockTextTops.get(i);
            for (String line : blockTitles.get(i)) {
                baseline += titleLineHeight();
                canvas.drawText(line, block.left + padding, baseline - TITLE_LINE_GAP_DP * density, title);
            }
            String details = blockDetails.get(i);
            if (details != null) {
                baseline += detailsLineHeight();
                canvas.drawText(details, block.left + padding, baseline - DETAILS_LINE_GAP_DP * density, time);
            }
            drawContinuationArrow(canvas, block, placements.get(i),
                    dimmed ? dimmedText : outlined ? lightText : darkText);
            if (blockCounts.get(i) != null) {
                drawCount(canvas, block, blockCounts.get(i), dimmed ? dimmedText : color);
            }
        }

        if (nowMinute != Event.NO_TIME) {
            float y = yFor(nowMinute);
            // A triangle pointing at the line, with a small gap before it.
            float tip = gutter - 3 * density;
            nowArrow.rewind();
            nowArrow.moveTo(tip - 7 * density, y - 5 * density);
            nowArrow.lineTo(tip, y);
            nowArrow.lineTo(tip - 7 * density, y + 5 * density);
            nowArrow.close();
            canvas.drawPath(nowArrow, nowPaint);
            canvas.drawLine(gutter, y, width, y, nowPaint);
        }
    }

    /** Writes a counted event's count in the bottom right corner of its block, if there is room. */
    private void drawCount(Canvas canvas, RectF block, String count, int color) {
        float padding = TEXT_PADDING_DP * density;
        if (block.height() < countPaint.getTextSize() + 2 * padding) {
            return;
        }
        countPaint.setColor(color);
        canvas.drawText(count, block.right - padding, block.bottom - padding, countPaint);
    }

    /**
     * Marks an overnight event's blocks: a small triangle whose tip touches the bottom edge, pointing
     * down, on the evening part, and one touching the top edge, pointing up, on the morning part,
     * centered across the block. Skipped when the block is too short to hold it.
     */
    private void drawContinuationArrow(Canvas canvas, RectF block, DayLayout.Placement placement,
                                       int color) {
        if (!placement.continuesAfter && !placement.continuesFrom) {
            return;
        }
        float height = continuationHeight();
        if (block.height() < 2 * height) {
            return;
        }
        float middle = block.centerX();
        float halfWidth = height * 0.9f;
        continuationArrow.rewind();
        if (placement.continuesAfter) {
            float tip = block.bottom;
            continuationArrow.moveTo(middle - halfWidth, tip - height);
            continuationArrow.lineTo(middle + halfWidth, tip - height);
            continuationArrow.lineTo(middle, tip);
        } else {
            float tip = block.top;
            continuationArrow.moveTo(middle - halfWidth, tip + height);
            continuationArrow.lineTo(middle + halfWidth, tip + height);
            continuationArrow.lineTo(middle, tip);
        }
        continuationArrow.close();
        blockPaint.setColor(color);
        canvas.drawPath(continuationArrow, blockPaint);
    }

    /** The triangle on an overnight event's blocks is a little shorter than the time text. */
    private float continuationHeight() {
        return timePaint.getTextSize() * 0.6f;
    }

    private float titleLineHeight() {
        return titlePaint.getTextSize() + TITLE_LINE_GAP_DP * density;
    }

    private float detailsLineHeight() {
        return timePaint.getTextSize() + DETAILS_LINE_GAP_DP * density;
    }

    private static String ellipsize(String text, TextPaint paint, float width) {
        return TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString();
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

    private String describe(List<DaySegment> segments) {
        if (segments.isEmpty()) {
            return getContext().getString(R.string.nothing_scheduled);
        }
        StringBuilder text = new StringBuilder();
        for (DayLayout.Placement placement : placements) {
            Event event = placement.event;
            text.append(event.getName()).append(", ")
                    .append(TimeFormat.range(event.getStartMinute(), event.getEndMinute()))
                    .append(". ");
        }
        return text.toString();
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
        if (event == null && placements.size() == 1) {
            event = placements.get(0).event;
        }
        if (event != null && listener != null) {
            Feedback.tick(getContext());
            listener.onBlockTapped(event);
            return true;
        }
        return false;
    }
}