package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;

/**
 * The up and down scrolling of one day. A touch on a gliding day freezes the glide, but the scroll
 * view then keeps every later movement for itself, so the pager couldn't change days. This hands
 * the touch back to the pager as soon as the finger moves more sideways than up or down.
 */
public class DayScrollView extends NestedScrollView {
    private final int touchSlop;
    private float downX;
    private float downY;

    public DayScrollView(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_MOVE) {
            float sideways = Math.abs(event.getX() - downX);
            float vertical = Math.abs(event.getY() - downY);
            ViewParent parent = getParent();
            if (parent != null && sideways > touchSlop && sideways > vertical) {
                // The scroll view asked the pager not to take over; the next move lets it.
                parent.requestDisallowInterceptTouchEvent(false);
            }
        }
        return handled;
    }
}
