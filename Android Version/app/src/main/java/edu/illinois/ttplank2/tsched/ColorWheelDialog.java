package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.view.animation.LinearInterpolator;
import android.graphics.Color;
import android.animation.ValueAnimator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Animator;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * A popup with a wheel of colors: each row has a color splotch on the left and its name on the
 * right. The rows sit on the rim of a turning drum, so they bunch together and flatten toward the
 * top and bottom, and the wheel clicks firmly to one row at a time. It loops forever: after the
 * last color comes the first again. Tapping a row picks it. Used both to pick an event's color and
 * to filter the calendar by color.
 *
 * <p>The Edit Names button at the bottom left turns every name into a text field. Save keeps the
 * new names (a blank one goes back to the color's own name); Cancel throws the edits away.
 */
public final class ColorWheelDialog {
    /** Not a color on the wheel: stands for "no color chosen", such as no filter. */
    public static final int CLEAR = 0;

    /** Called with the tapped color. */
    public interface Listener {
        void onPicked(int color);
    }

    /** Rows laid out on the drum; the outer ones are nearly faded away. */
    private static final int DRUM_ROWS = 7;
    /** Rows the popup shows: the drum pulls every row in, so nothing visible is cut off. */
    private static final int SHOWN_ROWS = 5;
    private static final int ROW_DP = 48;
    private static final int SPLOTCH_DP = 32;
    /** A hard flick moves the wheel at most this many rows: far enough to coast, still not loose. */
    /** How far round the drum each row is from its neighbor. */
    private static final float DRUM_STEP_DEGREES = 25f;
    /** The wheel has each color once, and repeats. */
    private static final int ROWS_PER_LOOP = EventColors.COLORS.length;
    /** How see-through a color is on a wheel that switches colors on and off, while it is off. */
    private static final float DIMMED_ALPHA = 0.3f;
    /** The width of the bright ring around a color that is on. */
    private static final float LIT_RING_DP = 3f;

    private ColorWheelDialog() {
    }

    /** Shows the wheel with a Cancel button, starting on {@code selected} if it is on the wheel. */
    public static void show(Context context, int titleRes, int selected, Listener listener,
                            Runnable onNamesSaved) {
        show(context, titleRes, selected, listener, R.string.cancel, null, onNamesSaved);
    }

    /**
     * Shows the wheel starting on {@code selected} (the first color if it is not on the wheel),
     * with a button on the right labeled {@code buttonRes} that closes it and then runs
     * {@code onButton}, and an Edit Names button on the left. {@code onNamesSaved} runs after the
     * user saves new names.
     */
    public static void show(Context context, int titleRes, int selected, Listener listener,
                            int buttonRes, Runnable onButton, Runnable onNamesSaved) {
        showWheel(context, titleRes, selected, listener, buttonRes, onButton, onNamesSaved, true, null, null);
    }

    /**
     * Shows the wheel for choosing several colors: tapping a color switches it on or off (it is flipped in
     * {@code chosen}, and then {@code onToggled} runs with it) and the popup stays open, with a Done button
     * to close it. The colors that are on are lit up with a bright ring and the rest are dimmed. Edit Names
     * is there too, since the colors show under the names the user gave them.
     */
    public static void showMulti(Context context, int titleRes, java.util.Set<Integer> chosen,
                                 Listener onToggled) {
        int start = chosen.isEmpty() ? CLEAR : chosen.iterator().next();
        showWheel(context, titleRes, start, onToggled, R.string.done, null, null, true, null, chosen);
    }

    /**
     * Shows the accent color wheel: only a Cancel button, no Edit Names, and every color under its
     * own original name, whatever names the user has given the colors elsewhere. Holding the
     * bottom left corner, where Edit Names would be, for {@link #SECRET_HOLD_MILLIS} swaps the
     * colors for the secret choices; picking one of those calls {@code onSecret} with its place in
     * the list of secrets.
     */
    public static void showAccent(Context context, int titleRes, int selected, Listener listener,
                                  Listener onSecret) {
        showWheel(context, titleRes, selected, listener, R.string.cancel, null, null, false, onSecret, null);
    }

    private static void showWheel(Context context, int titleRes, int selected, Listener listener,
                                  int buttonRes, Runnable onButton, Runnable onNamesSaved,
                                  boolean renamable, Listener onSecret, java.util.Set<Integer> chosen) {
        float density = context.getResources().getDisplayMetrics().density;
        int rowHeight = (int) (ROW_DP * density);
        WheelRecyclerView wheel = new WheelRecyclerView(context);
        wheel.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // Rows change in place when editing starts; a cross-fade would show old and new text together.
        wheel.setItemAnimator(null);
        // Padding above and below puts the row at the top of the list in the middle of the wheel.
        int padding = rowHeight * (DRUM_ROWS / 2);
        wheel.setPadding(0, padding, 0, padding);
        wheel.setClipToPadding(false);

        // The popup is only SHOWN_ROWS tall; the drum is taller, and the popup clips its faded ends.
        FrameLayout frame = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        rowHeight * SHOWN_ROWS, MeasureSpec.EXACTLY));
            }
        };
        FrameLayout.LayoutParams wheelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeight * DRUM_ROWS);
        wheelParams.topMargin = -rowHeight * ((DRUM_ROWS - SHOWN_ROWS) / 2);
        frame.addView(wheel, wheelParams);
        addMiddleRowLines(context, frame, rowHeight, density);

        // Both buttons get their real click handlers after show(), so they don't close the popup.
        MaterialAlertDialogBuilder builder = new FeedbackDialogBuilder(context)
                .setTitle(titleRes)
                .setView(frame)
                .setNegativeButton(buttonRes, null);
        if (renamable || onSecret != null) {
            // On the accent wheel this button is invisible: it is only the corner to hold.
            builder.setNeutralButton(R.string.edit_names, null);
        }
        AlertDialog dialog = builder.create();

        RowAdapter adapter = new RowAdapter(context, rowHeight, density, renamable, chosen, color -> {
            if (chosen == null) {
                dialog.dismiss();
                listener.onPicked(color);
            } else {
                // Several colors can be on, so the popup stays and the rows are drawn again.
                listener.onPicked(color);
                wheel.getAdapter().notifyDataSetChanged();
            }
        });
        wheel.setAdapter(adapter);
        new CoastSnapHelper().attachToRecyclerView(wheel);
        int[] centerRow = {RecyclerView.NO_POSITION};
        wheel.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                shapeRows(recyclerView, rowHeight);
                // Each color passing the middle vibrates (no sound), but only when the wheel is turning: putting
                // it in place as the popup opens stays silent.
                int row = centerRowOf(recyclerView);
                if (row != centerRow[0] && centerRow[0] != RecyclerView.NO_POSITION
                        && recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
                    Feedback.tickWithoutSound(context);
                }
                centerRow[0] = row;
            }
        });
        // Rows rebuilt for editing, or moved by typing, must take the drum shape again at once.
        ViewTreeObserver.OnGlobalLayoutListener reshape = () -> shapeRows(wheel, rowHeight);
        // While a name field has focus the wheel is locked, so typing can't move the colors.
        ViewTreeObserver.OnGlobalFocusChangeListener lockWhileTyping =
                (oldFocus, newFocus) -> wheel.setLocked(newFocus instanceof EditText);
        wheel.getViewTreeObserver().addOnGlobalLayoutListener(reshape);
        wheel.getViewTreeObserver().addOnGlobalFocusChangeListener(lockWhileTyping);
        dialog.setOnDismissListener(shown -> {
            wheel.getViewTreeObserver().removeOnGlobalLayoutListener(reshape);
            wheel.getViewTreeObserver().removeOnGlobalFocusChangeListener(lockWhileTyping);
        });

        // With nothing chosen, such as no filter yet, the wheel opens on the accent color.
        int startRow = startRowFor(selected, new AccentSetting(context).load());
        // Start in the middle of the endless list so the wheel can turn either way.
        ((LinearLayoutManager) wheel.getLayoutManager()).scrollToPositionWithOffset(
                positionFor(ROWS_PER_LOOP, startRow), 0);
        dialog.show();

        Button leftButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        Button rightButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (!renamable && onSecret != null) {
            wireSecretCorner(context, dialog, leftButton, wheel, adapter, onSecret);
        }
        Runnable stopEditing = () -> {
            InputMethodManager keyboard = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.hideSoftInputFromWindow(frame.getWindowToken(), 0);
            wheel.requestFocus();
            keepCentered(wheel, adapter::stopEditing);
            dialog.setTitle(titleRes);
            leftButton.setText(R.string.edit_names);
            rightButton.setText(buttonRes);
        };
        if (renamable && leftButton != null) {
            leftButton.setOnClickListener(view -> {
                if (adapter.isEditing()) {
                    if (!adapter.saveEdits()) {
                        Toast.makeText(context, R.string.save_failed, Toast.LENGTH_LONG).show();
                        return;
                    }
                    stopEditing.run();
                    if (onNamesSaved != null) {
                        onNamesSaved.run();
                    }
                } else {
                    // Name fields need the keyboard, which a popup without text fields won't allow.
                    // It is allowed only now, so opening the popup never disturbs a keyboard that
                    // was up behind it (that made the keyboard flash).
                    dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                    keepCentered(wheel, adapter::startEditing);
                    dialog.setTitle(R.string.edit_names_title);
                    leftButton.setText(R.string.save_names);
                    rightButton.setText(R.string.cancel);
                }
            });
        }
        rightButton.setOnClickListener(view -> {
            if (adapter.isEditing()) {
                stopEditing.run();
            } else {
                dialog.dismiss();
                if (onButton != null) {
                    onButton.run();
                }
            }
        });
    }

    /** How long the corner must be held to reveal the secret colors (three quick taps do it at once). */
    static final long SECRET_HOLD_MILLIS = 7000;
    private static final long SECRET_FADE_BACK_MILLIS = 450;

    /**
     * Makes {@code corner} (the invisible button in the dialog's bottom left) reveal the secret
     * colors when it is held for {@link #SECRET_HOLD_MILLIS}. While it is held, everything in the
     * popup slowly fades into the popup's background; letting go early fades it back, and holding
     * to the end swaps the wheel's colors for the secret ones and fades in.
     */
    private static void wireSecretCorner(Context context, AlertDialog dialog, Button corner,
                                         RecyclerView wheel, RowAdapter adapter, Listener onSecret) {
        corner.setTextColor(Color.TRANSPARENT);
        corner.setBackground(null);
        corner.setContentDescription(null);
        corner.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // The dialog would close on a tap of its own buttons; this one does nothing when tapped.
        corner.setOnClickListener(view -> { });
        View contents = dialog.findViewById(androidx.appcompat.R.id.parentPanel);
        if (contents == null) {
            return;
        }
        ValueAnimator[] fading = {null};
        boolean[] revealed = {false};
        // Three quick taps skip the 7-second hold and show the secret colors at once.
        TripleTap tripleTap = new TripleTap(ViewConfiguration.getDoubleTapTimeout());
        Runnable reveal = () -> {
            revealed[0] = true;
            adapter.showSecret(secretEntries(), onSecret);
            dialog.setTitle(R.string.secret_colors);
            ((LinearLayoutManager) wheel.getLayoutManager()).scrollToPositionWithOffset(
                    positionFor(AccentColors.SECRET_KEYS.length,
                            Math.max(0, AccentColors.secretIndex(
                                    new AccentSetting(context).loadKey()))), 0);
        };
        Runnable fadeBack = () -> {
            if (fading[0] != null) {
                fading[0].removeAllListeners();
                fading[0].cancel();
            }
            ValueAnimator back = ValueAnimator.ofFloat(contents.getAlpha(), 1f);
            back.setDuration(SECRET_FADE_BACK_MILLIS);
            back.addUpdateListener(animation -> contents.setAlpha((float) animation.getAnimatedValue()));
            fading[0] = back;
            back.start();
        };
        corner.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (revealed[0]) {
                        return true;
                    }
                    if (tripleTap.tap(SystemClock.uptimeMillis())) {
                        if (fading[0] != null) {
                            fading[0].removeAllListeners();
                            fading[0].cancel();
                        }
                        reveal.run();
                        // Whatever the taps faded out comes straight back in with the secret colors.
                        contents.setAlpha(1f);
                        return true;
                    }
                    if (fading[0] != null) {
                        fading[0].removeAllListeners();
                        fading[0].cancel();
                    }
                    ValueAnimator out = ValueAnimator.ofFloat(contents.getAlpha(), 0f);
                    out.setInterpolator(new LinearInterpolator());
                    out.setDuration((long) (SECRET_HOLD_MILLIS * contents.getAlpha()));
                    out.addUpdateListener(animation -> contents.setAlpha((float) animation.getAnimatedValue()));
                    out.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            reveal.run();
                            fadeBack.run();
                        }
                    });
                    fading[0] = out;
                    out.start();
                    return true;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    if (!revealed[0]) {
                        fadeBack.run();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (!revealed[0]) {
                        fadeBack.run();
                    }
                    return true;
                default:
                    return true;
            }
        });
    }

    private static Entry[] secretEntries() {
        Entry[] entries = new Entry[AccentColors.SECRET_KEYS.length];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = new Entry(AccentColors.SECRET_NAMES[i], AccentColors.SECRET_SWATCHES[i], i);
        }
        return entries;
    }

    /** The adapter position, near the middle of the endless list, of {@code row} in a loop of {@code loop} rows. */
    private static int positionFor(int loop, int row) {
        int middle = Integer.MAX_VALUE / 2;
        return middle - middle % loop + row;
    }

    /** One row of a wheel that isn't the usual colors: its name, swatch colors, and what it stands for. */
    private static final class Entry {
        final String name;
        final int[] colors;
        final int index;

        Entry(String name, int[] colors, int index) {
            this.name = name;
            this.colors = colors;
            this.index = index;
        }
    }

    /** Runs {@code change}, which rebinds the rows, then puts the same row back in the middle. */
    private static void keepCentered(RecyclerView wheel, Runnable change) {
        float middle = wheel.getHeight() / 2f;
        int centered = RecyclerView.NO_POSITION;
        float nearest = Float.MAX_VALUE;
        for (int i = 0; i < wheel.getChildCount(); i++) {
            View row = wheel.getChildAt(i);
            float distance = Math.abs((row.getTop() + row.getBottom()) / 2f - middle);
            if (distance < nearest) {
                nearest = distance;
                centered = wheel.getChildAdapterPosition(row);
            }
        }
        change.run();
        if (centered != RecyclerView.NO_POSITION) {
            ((LinearLayoutManager) wheel.getLayoutManager()).scrollToPositionWithOffset(centered, 0);
        }
    }

    /**
     * The row the wheel opens on: {@code selected} if it is on the wheel, otherwise the accent
     * color's row, or the first row if neither is on the wheel.
     */
    static int startRowFor(int selected, int accent) {
        for (int i = 0; i < EventColors.COLORS.length; i++) {
            if (EventColors.COLORS[i] == selected) {
                return i;
            }
        }
        return rowOf(accent);
    }

    private static int rowOf(int color) {
        for (int i = 0; i < EventColors.COLORS.length; i++) {
            if (EventColors.COLORS[i] == color) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Makes the wheel look round. A row k rows from the middle sits k steps round a drum, so it is
     * pushed toward the middle and flattened by the angle, and fades as it turns away.
     */
    /** The adapter position of the row closest to the middle of the wheel. */
    private static int centerRowOf(RecyclerView wheel) {
        float middle = wheel.getPaddingTop() + (wheel.getHeight() - wheel.getPaddingTop()
                - wheel.getPaddingBottom()) / 2f;
        int closest = RecyclerView.NO_POSITION;
        float closestDistance = Float.MAX_VALUE;
        for (int i = 0; i < wheel.getChildCount(); i++) {
            View row = wheel.getChildAt(i);
            float distance = Math.abs((row.getTop() + row.getBottom()) / 2f - middle);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = wheel.getChildAdapterPosition(row);
            }
        }
        return closest;
    }

    private static void shapeRows(RecyclerView wheel, int rowHeight) {
        float stepRadians = (float) Math.toRadians(DRUM_STEP_DEGREES);
        float radius = rowHeight / stepRadians;
        float quarterTurn = (float) (Math.PI / 2);
        float middle = wheel.getHeight() / 2f;
        boolean editing = wheel.getAdapter() instanceof RowAdapter
                && ((RowAdapter) wheel.getAdapter()).isEditing();
        for (int i = 0; i < wheel.getChildCount(); i++) {
            View row = wheel.getChildAt(i);
            float rowsFromMiddle = ((row.getTop() + row.getBottom()) / 2f - middle) / rowHeight;
            float angle = Math.max(-quarterTurn, Math.min(quarterTurn, rowsFromMiddle * stepRadians));
            float flatten = (float) Math.cos(angle);
            row.setTranslationY(radius * (float) Math.sin(angle) - rowsFromMiddle * rowHeight);
            row.setScaleY(flatten);
            row.setScaleX(0.8f + 0.2f * flatten);
            row.setAlpha(flatten);
            // While editing names, only the field in the middle takes typing; the others pass taps to their rows.
            if (editing && row.getTag() instanceof EditText) {
                EditText field = (EditText) row.getTag();
                boolean inMiddle = Math.abs(rowsFromMiddle) < 0.5f;
                // A disabled field lets taps through to its row, which brings it to the middle.
                if (field.isEnabled() != inMiddle) {
                    field.setEnabled(inMiddle);
                    field.setFocusable(inMiddle);
                    field.setFocusableInTouchMode(inMiddle);
                    field.setClickable(inMiddle);
                    field.setLongClickable(inMiddle);
                }
            }
        }
    }

    /** Two thin lines above and below the middle row, marking where a choice rests. */
    private static void addMiddleRowLines(Context context, FrameLayout frame, int rowHeight,
                                          float density) {
        int lineColor = ContextCompat.getColor(context, R.color.text_secondary);
        int top = rowHeight * (SHOWN_ROWS / 2);
        for (int y : new int[] {top, top + rowHeight}) {
            View line = new View(context);
            line.setBackgroundColor(lineColor);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) density));
            params.topMargin = y;
            params.setMarginStart((int) (24 * density));
            params.setMarginEnd((int) (24 * density));
            frame.addView(line, params);
        }
    }

    /**
     * A wheel that never scrolls itself to reveal a name field, and that can be locked so a swipe
     * can't turn it while the user types.
     */
    private static final class WheelRecyclerView extends RecyclerView {
        private final LockableLayoutManager manager;
        /** The row a locked touch went down on, and where, to tell whether it ends as a tap. */
        private View tappedRow;
        private View clickedRow;
        private float tapDownX;
        private float tapDownY;

        WheelRecyclerView(Context context) {
            super(context);
            manager = new LockableLayoutManager(context);
            setLayoutManager(manager);
            // The wheel can hold focus itself, so letting go of a name field doesn't jump to another one.
            setFocusableInTouchMode(true);
            setDefaultFocusHighlightEnabled(false);
            setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
        }

        void setLocked(boolean locked) {
            manager.locked = locked;
            if (locked) {
                stopScroll();
            }
        }

        /**
         * While locked, a swipe does nothing, but a tap on another row still counts as a tap on
         * it, so that row can be brought to the middle. A tap on the name being edited goes to it.
         */
        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (manager.locked && event.getActionMasked() == MotionEvent.ACTION_DOWN
                    && !isOnFocusedField(event)) {
                tapDownX = event.getX();
                tapDownY = event.getY();
                tappedRow = findChildViewUnder(event.getX(), event.getY());
                return true;
            }
            return super.onInterceptTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (tappedRow == null) {
                return super.onTouchEvent(event);
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP) {
                View row = tappedRow;
                tappedRow = null;
                float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                boolean stayed = Math.abs(event.getX() - tapDownX) <= slop
                        && Math.abs(event.getY() - tapDownY) <= slop;
                if (stayed && findChildViewUnder(event.getX(), event.getY()) == row) {
                    clickedRow = row;
                    performClick();
                }
            } else if (action == MotionEvent.ACTION_CANCEL) {
                tappedRow = null;
            }
            return true;
        }

        /** A tap on a row while locked arrives here, so accessibility services can trigger it too. */
        @Override
        public boolean performClick() {
            boolean handled = super.performClick();
            if (clickedRow != null) {
                View row = clickedRow;
                clickedRow = null;
                row.performClick();
                return true;
            }
            return handled;
        }

        /** Lets the wheel move itself to a tapped row even while a swipe is locked out. */
        void setMovingToRow(boolean moving) {
            manager.movingToRow = moving;
        }

        private boolean isOnFocusedField(MotionEvent event) {
            View focused = findFocus();
            if (!(focused instanceof EditText)) {
                return false;
            }
            Rect bounds = new Rect();
            if (!focused.getGlobalVisibleRect(bounds)) {
                return false;
            }
            int[] windowAt = new int[2];
            getRootView().getLocationOnScreen(windowAt);
            bounds.offset(windowAt[0], windowAt[1]);
            return bounds.contains((int) event.getRawX(), (int) event.getRawY());
        }

        @Override
        public boolean requestChildRectangleOnScreen(@NonNull View child, @NonNull Rect rect,
                                                     boolean immediate) {
            return false;
        }
    }

    /** A vertical list whose touch scrolling can be switched off. */
    private static final class LockableLayoutManager extends LinearLayoutManager {
        boolean locked;
        /** True while the wheel moves a tapped row to the middle, which a lock doesn't stop. */
        boolean movingToRow;

        LockableLayoutManager(Context context) {
            super(context);
        }

        @Override
        public boolean canScrollVertically() {
            return (!locked || movingToRow) && super.canScrollVertically();
        }
    }

    /**
     * Lets a flick coast freely, like the calendar's scrolling, and only then settles the nearest
     * row into the middle once the wheel has slowed to a stop.
     */
    private static final class CoastSnapHelper extends LinearSnapHelper {
        @Override
        public boolean onFling(int velocityX, int velocityY) {
            return false;
        }
    }

    private static final class RowAdapter extends RecyclerView.Adapter<RowHolder> {
        private final Context context;
        private final int rowHeight;
        private final float density;
        private final Listener listener;
        /** The colors that are on, or null on a wheel that picks just one color. */
        private final java.util.Set<Integer> chosen;
        /** The secret rows shown instead of the colors, or null while the usual colors show. */
        private Entry[] secretEntries;
        private Listener secretListener;
        /** The names the user gave the colors, or null on a wheel that shows only original names. */
        private final ColorNames colorNames;
        /** What each name field holds while editing, by color. */
        private final String[] pendingNames = new String[ROWS_PER_LOOP];
        private boolean editing;
        private RecyclerView wheel;

        RowAdapter(Context context, int rowHeight, float density, boolean renamable,
                   java.util.Set<Integer> chosen, Listener listener) {
            this.chosen = chosen;
            this.context = context;
            this.rowHeight = rowHeight;
            this.density = density;
            this.listener = listener;
            this.colorNames = renamable ? new ColorNames(context) : null;
        }

        private String nameOf(int color) {
            return colorNames != null ? colorNames.nameOf(color) : EventColors.nameOf(color);
        }

        /** Whether the name shows in full brightness: one the user chose, or on a wheel with no renaming. */
        private boolean brightName(int color) {
            return colorNames == null || colorNames.hasCustomName(color);
        }

        /** Swaps the wheel's colors for {@code entries}; picking one calls {@code onPicked} with its index. */
        void showSecret(Entry[] entries, Listener onPicked) {
            secretEntries = entries;
            secretListener = onPicked;
            notifyItemRangeChanged(0, getItemCount());
        }

        private void bindSecret(RowHolder holder, int position) {
            Entry entry = secretEntries[position % secretEntries.length];
            holder.splotch.setBackground(entry.colors.length == 1 ? splotch(entry.colors[0])
                    : new SwatchDrawable(entry.colors, 8 * density, Math.max(1, density),
                            ContextCompat.getColor(context, R.color.text_secondary)));
            holder.splotch.setAlpha(1f);
            holder.name.setTypeface(null, android.graphics.Typeface.NORMAL);
            holder.name.setText(entry.name);
            holder.name.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            holder.name.setVisibility(View.VISIBLE);
            holder.nameField.setVisibility(View.GONE);
            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(view -> {
                listener.onPicked(0);
                secretListener.onPicked(entry.index);
            });
        }

        boolean isEditing() {
            return editing;
        }

        void startEditing() {
            // A color still using its own name starts empty, with that name shown as the dim hint,
            // so a new name can be typed without deleting anything first.
            for (int i = 0; i < ROWS_PER_LOOP; i++) {
                String name = nameOf(EventColors.COLORS[i]);
                pendingNames[i] = name.equals(EventColors.NAMES[i]) ? "" : name;
            }
            editing = true;
            refreshRows();
        }

        void stopEditing() {
            editing = false;
            refreshRows();
        }

        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            wheel = recyclerView;
        }

        /** Rebinds the rows on screen and a few beyond, which are the only ones that exist. */
        private void refreshRows() {
            LinearLayoutManager manager = (LinearLayoutManager) wheel.getLayoutManager();
            int first = manager.findFirstVisibleItemPosition();
            int last = manager.findLastVisibleItemPosition();
            if (first != RecyclerView.NO_POSITION) {
                int from = Math.max(0, first - ROWS_PER_LOOP);
                notifyItemRangeChanged(from, last + ROWS_PER_LOOP - from + 1);
            }
        }

        /** Turns the row at {@code position} to the middle of the wheel, then starts editing its name. */
        private void centerAndEdit(int position) {
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            RecyclerView.ViewHolder row = wheel.findViewHolderForAdapterPosition(position);
            if (row == null) {
                return;
            }
            float fromMiddle = (row.itemView.getTop() + row.itemView.getBottom()) / 2f
                    - wheel.getHeight() / 2f;
            if (Math.abs(fromMiddle) < 2f) {
                editName((RowHolder) row);
                return;
            }
            wheel.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                        return;
                    }
                    recyclerView.removeOnScrollListener(this);
                    ((WheelRecyclerView) recyclerView).setMovingToRow(false);
                    RecyclerView.ViewHolder arrived =
                            recyclerView.findViewHolderForAdapterPosition(position);
                    if (arrived != null) {
                        editName((RowHolder) arrived);
                    }
                }
            });
            // Aligning the row's top with the top of the padded area puts it in the middle.
            LinearSmoothScroller scroller = new LinearSmoothScroller(context) {
                @Override
                protected int getVerticalSnapPreference() {
                    return SNAP_TO_START;
                }
            };
            scroller.setTargetPosition(position);
            ((WheelRecyclerView) wheel).setMovingToRow(true);
            wheel.getLayoutManager().startSmoothScroll(scroller);
        }

        /** Puts the cursor in the row's name field and brings up the keyboard. */
        private void editName(RowHolder row) {
            EditText field = row.nameField;
            field.setEnabled(true);
            field.setFocusable(true);
            field.setFocusableInTouchMode(true);
            field.setClickable(true);
            field.setLongClickable(true);
            field.requestFocus();
            field.setSelection(field.getText().length());
            InputMethodManager keyboard = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.showSoftInput(field, 0);
        }

        /**
         * Keeps the edited names; a blank one returns to the color's own name.
         *
         * @return false if the names couldn't be saved
         */
        boolean saveEdits() {
            return colorNames.renameAll(EventColors.COLORS, pendingNames);
        }

        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (56 * density), 0, (int) (24 * density), 0);
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, rowHeight));
            row.setForeground(whiteFlash(context));
            row.setFocusable(true);

            View splotch = new View(context);
            int size = (int) (SPLOTCH_DP * density);
            row.addView(splotch, new LinearLayout.LayoutParams(size, size));

            TextView name = new TextView(context);
            name.setTextSize(20);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            row.addView(name, nameParams());

            EditText nameField = new EditText(context);
            nameField.setTextSize(20);
            nameField.setSingleLine(true);
            nameField.setEllipsize(TextUtils.TruncateAt.END);
            nameField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            // Sideways, the keyboard must not take over the whole screen: the wheel stays in view.
            nameField.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                    | EditorInfo.IME_FLAG_NO_FULLSCREEN);
            // Done puts the keyboard away and lets go of the field, which unlocks the wheel.
            nameField.setOnEditorActionListener((field, actionId, event) -> {
                if (actionId != EditorInfo.IME_ACTION_DONE) {
                    return false;
                }
                InputMethodManager keyboard = (InputMethodManager)
                        context.getSystemService(Context.INPUT_METHOD_SERVICE);
                keyboard.hideSoftInputFromWindow(field.getWindowToken(), 0);
                wheel.requestFocus();
                return true;
            });
            nameField.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            nameField.setHintTextColor(ContextCompat.getColor(context, R.color.text_dim));
            nameField.setVisibility(View.GONE);
            row.addView(nameField, nameParams());
            row.setTag(nameField);
            return new RowHolder(row, splotch, name, nameField);
        }

        /** The name sits right of the splotch and takes the rest of the row, ending in "..." if long. */
        private LinearLayout.LayoutParams nameParams() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMarginStart((int) (20 * density));
            return params;
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            if (secretEntries != null) {
                bindSecret(holder, position);
                return;
            }
            int row = position % ROWS_PER_LOOP;
            int color = EventColors.COLORS[row];
            // On a wheel that switches colors on and off, the ones that are on are lit (a bright ring and bold
            // name) and the rest are dimmed.
            boolean on = chosen != null && chosen.contains(color);
            holder.splotch.setBackground(splotch(color, on));
            holder.splotch.setAlpha(chosen == null || on ? 1f : DIMMED_ALPHA);
            holder.name.setText(nameOf(color));
            holder.name.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            // A color still on its original name is dim, like the hint in the name editor.
            holder.name.setTextColor(ContextCompat.getColor(context, chosen != null && !on
                    ? R.color.text_secondary
                    : brightName(color) ? R.color.text_primary : R.color.text_dim));
            holder.name.setVisibility(editing ? View.GONE : View.VISIBLE);

            holder.nameField.removeTextChangedListener(holder.watcher);
            holder.nameField.setVisibility(editing ? View.VISIBLE : View.GONE);
            holder.nameField.setHint(EventColors.NAMES[row]);
            holder.nameField.setText(editing ? pendingNames[row] : "");
            holder.watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable text) {
                    pendingNames[row] = text.toString();
                }
            };
            holder.nameField.addTextChangedListener(holder.watcher);

            // A tap picks the color; while editing names it brings that color to the middle instead.
            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(view -> {
                if (editing) {
                    centerAndEdit(holder.getBindingAdapterPosition());
                } else {
                    listener.onPicked(color);
                }
            });
        }

        @Override
        public int getItemCount() {
            return Integer.MAX_VALUE;
        }

        private GradientDrawable splotch(int color) {
            return splotch(color, false);
        }

        /** A rounded square of {@code color}; lit with a thick bright ring when {@code lit}. */
        private GradientDrawable splotch(int color, boolean lit) {
            GradientDrawable square = new GradientDrawable();
            square.setShape(GradientDrawable.RECTANGLE);
            square.setCornerRadius(8 * density);
            square.setColor(color);
            square.setStroke((int) Math.max(1, (lit ? LIT_RING_DP : 1) * density),
                    ContextCompat.getColor(context, lit ? R.color.text_primary : R.color.text_secondary));
            return square;
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        final View splotch;
        final TextView name;
        final EditText nameField;
        TextWatcher watcher;

        RowHolder(View itemView, View splotch, TextView name, EditText nameField) {
            super(itemView);
            this.splotch = splotch;
            this.name = name;
            this.nameField = nameField;
        }
    }

    /** The white press flash every control in the app uses. */
    private static RippleDrawable whiteFlash(Context context) {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setColor(0xFFFFFFFF);
        return new RippleDrawable(
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white_flash)), null, mask);
    }
}
