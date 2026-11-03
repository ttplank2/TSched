package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

import androidx.annotation.NonNull;

/**
 * A scroll view that is only as tall as its content, up to a share of the screen's height, so
 * that whatever sits below it in a popup (such as a row of buttons) is never pushed off screen.
 */
public class MaxHeightScrollView extends ScrollView {
    /** The most of the screen's height this view takes. */
    private static final float MAX_SCREEN_SHARE = 0.45f;

    public MaxHeightScrollView(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * MAX_SCREEN_SHARE);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
    }
}
