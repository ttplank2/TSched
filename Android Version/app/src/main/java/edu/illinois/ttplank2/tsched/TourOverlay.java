package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * A guided tour's cover for a whole screen: a dark see-through layer with a bright hole around the
 * control being explained, and a card of words beside it with Skip, Back and Next. It takes every
 * touch, so the screen underneath can't be used until the tour is over.
 */
final class TourOverlay extends FrameLayout {
    private static final int SCRIM_COLOR = 0xCC000000;
    private static final float CARD_MAX_WIDTH_DP = 360f;
    private static final float CARD_MARGIN_DP = 16f;
    private static final float CARD_GAP_DP = 14f;
    private static final float SPOT_PADDING_DP = 2f;
    private static final float SPOT_CORNER_DP = 16f;
    private static final float RING_DP = 2f;
    /** How far in from the screen's edge the lit-up outline stays, so it is never cut off. */
    private static final float SPOT_EDGE_INSET_DP = 3f;

    private final float density;
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path scrim = new Path();
    private final RectF spot = new RectF();
    private boolean hasSpot;

    private final View card;
    private final TextView counter;
    private final TextView title;
    private final TextView body;
    private final Button skipButton;
    private final Button backButton;
    private final Button nextButton;

    TourOverlay(@NonNull Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        scrimPaint.setColor(SCRIM_COLOR);
        ringPaint.setColor(MaterialColors.getColor(context, R.attr.appAccentOutline, EventColors.DEFAULT));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(RING_DP * density);

        card = LayoutInflater.from(context).inflate(R.layout.tour_card, this, false);
        counter = card.findViewById(R.id.tour_counter);
        title = card.findViewById(R.id.tour_title);
        body = card.findViewById(R.id.tour_body);
        skipButton = card.findViewById(R.id.tour_skip);
        backButton = card.findViewById(R.id.tour_back);
        nextButton = card.findViewById(R.id.tour_next);
        addView(card);
    }

    void setButtonListeners(OnClickListener skip, OnClickListener back, OnClickListener next) {
        skipButton.setOnClickListener(skip);
        backButton.setOnClickListener(back);
        nextButton.setOnClickListener(next);
    }

    /**
     * Shows one step. {@code target} is the bounds of the control to light up, in this view's own
     * coordinates, or null for a card in the middle of the screen with nothing lit.
     */
    void showStep(@Nullable RectF target, CharSequence counterText, CharSequence titleText,
                  CharSequence bodyText, boolean isFirst, boolean isLast, int nextLabelRes) {
        hasSpot = target != null;
        if (hasSpot) {
            float pad = SPOT_PADDING_DP * density;
            spot.set(target.left - pad, target.top - pad, target.right + pad, target.bottom + pad);
        }
        counter.setText(counterText);
        title.setText(titleText);
        body.setText(bodyText);
        backButton.setVisibility(isFirst ? GONE : VISIBLE);
        // The last step has nothing left to skip.
        skipButton.setVisibility(isLast ? INVISIBLE : VISIBLE);
        nextButton.setText(nextLabelRes);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        float margin = CARD_MARGIN_DP * density;
        int cardWidth = (int) Math.min(width - 2 * margin, CARD_MAX_WIDTH_DP * density);
        card.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((int) (height - 2 * margin), MeasureSpec.AT_MOST));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int cardWidth = card.getMeasuredWidth();
        int cardHeight = card.getMeasuredHeight();
        float margin = CARD_MARGIN_DP * density;
        float gap = CARD_GAP_DP * density;
        // Keep the card clear of the system's own bars.
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        int bottomBar = insets == null ? 0
                : insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
        int topBar = insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
        float lowest = height - bottomBar - margin;
        float highest = topBar + margin;

        float cardTop;
        if (!hasSpot) {
            cardTop = (height - cardHeight) / 2f;
        } else if (lowest - (spot.bottom + gap) >= cardHeight) {
            cardTop = spot.bottom + gap;
        } else if (spot.top - gap - highest >= cardHeight) {
            cardTop = spot.top - gap - cardHeight;
        } else {
            cardTop = lowest - cardHeight;
        }
        // However it was placed, the card stays wholly on the screen, clear of the system bars.
        cardTop = Math.max(highest, Math.min(cardTop, lowest - cardHeight));
        int cardLeft = (width - cardWidth) / 2;
        card.layout(cardLeft, (int) cardTop, cardLeft + cardWidth, (int) cardTop + cardHeight);
    }

    /**
     * The lit-up outline, kept in from the screen's edge: a big control, such as the day cards, can
     * reach it, and the outline must never be cut off.
     */
    private RectF visibleSpot() {
        RectF visible = new RectF(spot);
        float inset = SPOT_EDGE_INSET_DP * density;
        visible.intersect(inset, inset, getWidth() - inset, getHeight() - inset);
        return visible;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        scrim.reset();
        scrim.setFillType(Path.FillType.EVEN_ODD);
        scrim.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        float corner = SPOT_CORNER_DP * density;
        RectF lit = hasSpot ? visibleSpot() : null;
        if (lit != null) {
            scrim.addRoundRect(lit, corner, corner, Path.Direction.CW);
        }
        canvas.drawPath(scrim, scrimPaint);
        if (lit != null) {
            canvas.drawRoundRect(lit, corner, corner, ringPaint);
        }
    }

    /** Takes every touch, so the screen underneath stays out of reach. */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
