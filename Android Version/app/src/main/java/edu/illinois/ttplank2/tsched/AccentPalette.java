package edu.illinois.ttplank2.tsched;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Brings the secret accent choices to a screen's views, which the theme alone can't do. Buttons
 * wear the choice's first color and standalone text its second; a title's letters run from the
 * first color to the second and back. Fruity instead cycles through the fruit colors live, with
 * every accent-colored part of the screen showing the same color at the same moment, so a
 * screenshot of it looks like a single accent color. Christmas also has a few small snowflakes
 * falling over the screen and Halloween some little cobwebs, which are only decoration. Only the parts that wear the accent change:
 * a view's other looks, such as a disabled button's gray, are kept as they were.
 *
 * <p>Popups (such as the clock) are gray while Fruity runs, since they can't follow the live color.
 */
final class AccentPalette {
    /** How long Fruity takes to go through all six fruit colors once. */
    private static final long FRUITY_LOOP_MILLIS = 42000;
    /** Text at least this large counts as a title (in sp). */
    private static final float TITLE_SP = 20;
    private static final int ENABLED = android.R.attr.state_enabled;
    private static final int[] DISABLED = {-ENABLED};
    private static final int[] SELECTED = {ENABLED, android.R.attr.state_selected};
    private static final int[] USUAL = {ENABLED};
    private static final int[][] LOOKS = {DISABLED, SELECTED, USUAL, {}};

    /** The ring of an unchosen radio button or checkbox once its chosen look is recolored. */
    private static final int UNCHECKED_GRAY = 0xFF9E9E9E;

    /** The gray a popup's accent parts are while Fruity runs. */
    private static final int POPUP_GRAY = AccentColors.GRAY;

    /** When Fruity's cycle began, on {@link SystemClock#elapsedRealtime}; it is on gray then; not set until first needed. */
    private static long fruityOrigin = -1;

    /** The screen currently being colored, so popups can join it. */
    private static AccentPalette active;

    /** What a view looked like before it was recolored, and which of its colors are the accent. */
    private static final class Looks {
        final ColorStateList fill;
        final ColorStateList stroke;
        final ColorStateList text;
        final boolean title;
        final boolean icon;
        /** A row of a single-choice list, whose check mark shows the accent. */
        final boolean check;

        Looks(ColorStateList fill, ColorStateList stroke, ColorStateList text, boolean title, boolean icon,
              boolean check) {
            this.fill = fill;
            this.stroke = stroke;
            this.text = text;
            this.title = title;
            this.icon = icon;
            this.check = check;
        }
    }

    private static final Looks NOTHING = new Looks(null, null, null, false, false, false);

    private final View root;
    private final int main;
    private final int text;
    private final int outline;
    private final int[] pair;
    /** Whether titles alternate their letters between two colors (Christmas) rather than shading. */
    private int[] titlePair;
    private boolean candyCane;
    /** Whether all accent text, not just titles, shades between the two colors (Gold). */
    private boolean shadeAllText;
    private int[] candyCaneColors;
    private final List<View> popups = new ArrayList<>();
    private final ViewTreeObserver.OnGlobalLayoutListener onLayout = this::paintAll;
    /**
     * A reused title (a day page coming back into view) changes its text without a new layout, so its
     * letter colors are checked before every draw too.
     */
    private final ViewTreeObserver.OnPreDrawListener onDraw = this::restoreAllTitles;
    private ValueAnimator fruity;
    private int fruityColor;
    private boolean inPopup;

    private AccentPalette(View root, int main, int text, int outline, int[] pair) {
        this.root = root;
        this.main = main;
        this.text = text;
        this.outline = outline;
        this.pair = pair;
    }

    /**
     * Starts coloring {@code root} for the secret choice {@code key}: returns null for a color of the
     * wheel, which the theme already handles. {@code main}, {@code text} and {@code outline} are the
     * theme's accent colors that views start out wearing.
     */
    static AccentPalette start(View root, String key, int main, int text, int outline) {
        int secret = AccentColors.secretIndex(key);
        if (secret < 0) {
            return null;
        }
        int[] colors = AccentColors.secretColors(secret);
        AccentPalette palette = new AccentPalette(root, main, text, outline, new int[] {colors[0], colors[1]});
        palette.titlePair = AccentColors.titleColors(secret);
        palette.candyCane = AccentColors.isChristmas(secret);
        palette.shadeAllText = AccentColors.isGold(secret);
        palette.candyCaneColors = AccentColors.candyCaneColors();
        root.getViewTreeObserver().addOnGlobalLayoutListener(palette.onLayout);
        if (!AccentColors.isFruity(secret)) {
            root.getViewTreeObserver().addOnPreDrawListener(palette.onDraw);
        }
        if (AccentColors.isFruity(secret)) {
            palette.startFruity();
        }
        palette.paintAll();
        active = palette;
        return palette;
    }

    /**
     * Puts the decoration of the secret choice {@code key} over {@code root}: Christmas's falling
     * snow or Halloween's cobwebs (nothing for other choices), and returns it, or null. It stays for
     * the whole life of the screen, not just while it is resumed, so it is there from the first
     * frame of a screen change and keeps showing on the screen that is leaving.
     */
    static View addDecoration(ViewGroup root, String key, boolean withSpider) {
        int secret = AccentColors.secretIndex(key);
        View decoration;
        if (AccentColors.isChristmas(secret)) {
            decoration = new SnowView(root.getContext());
        } else if (AccentColors.isHalloween(secret)) {
            decoration = new WebsView(root.getContext(), withSpider);
        } else {
            return null;
        }
        root.addView(decoration, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return decoration;
    }

    void stop() {
        if (fruity != null) {
            fruity.cancel();
            fruity = null;
        }
        if (root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(onLayout);
            root.getViewTreeObserver().removeOnPreDrawListener(onDraw);
        }
        if (active == this) {
            active = null;
        }
    }

    /**
     * Makes {@code dialog} wear the accent colors while it is open. Call this when the dialog is
     * made; it takes effect when the dialog is shown.
     */
    static void watchDialog(Dialog dialog) {
        View decor = dialog.getWindow().getDecorView();
        ViewTreeObserver.OnGlobalLayoutListener repaint = () -> {
            if (active != null) {
                active.paintPopup(decor);
            }
        };
        decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                if (active != null) {
                    active.popupOpened(decor);
                    decor.getViewTreeObserver().addOnGlobalLayoutListener(repaint);
                }
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                if (decor.getViewTreeObserver().isAlive()) {
                    decor.getViewTreeObserver().removeOnGlobalLayoutListener(repaint);
                }
                if (active != null) {
                    active.popupClosed(decor);
                }
            }
        });
    }

    /**
     * The context to make a popup with that only the theme can color, such as the clock: while Fruity
     * is showing it carries a gray accent, since the popup can't follow the live color; otherwise it
     * is {@code context}.
     */
    static Context popupContext(Context context) {
        if (active == null || active.fruity == null) {
            return context;
        }
        return new ContextThemeWrapper(context, R.style.ThemeOverlay_TSched_AccentPopupGray);
    }

    private void popupOpened(View decor) {
        popups.add(decor);
        paintPopup(decor);
    }

    private void popupClosed(View decor) {
        popups.remove(decor);
    }

    /** Keeps {@code view} out of the recoloring, for a view whose colors the screen sets itself. */
    static void leaveAlone(View view) {
        view.setTag(R.id.accent_role, NOTHING);
    }

    /**
     * Where Fruity is in its cycle, as a place in {@link AccentColors#fruitColors()} (gray is
     * place 3). It follows one clock for the whole app, so every screen carries on from the same point.
     */
    private static float fruityPlace() {
        if (fruityOrigin < 0) {
            restartFruity();
        }
        long elapsed = SystemClock.elapsedRealtime() - fruityOrigin;
        float turns = (elapsed % FRUITY_LOOP_MILLIS) / (float) FRUITY_LOOP_MILLIS;
        int count = AccentColors.fruitColors().length;
        return (AccentColors.GRAY_PLACE + turns * count) % count;
    }

    /** Starts Fruity over from gray, such as when it is chosen again. */
    static void restartFruity() {
        fruityOrigin = SystemClock.elapsedRealtime();
    }

    private static int fruityColorAt(float place) {
        int[] fruits = AccentColors.fruitColors();
        int from = (int) Math.floor(place);
        return (int) new ArgbEvaluator().evaluate(place - from, fruits[from % fruits.length],
                fruits[(from + 1) % fruits.length]);
    }

    private void startFruity() {
        fruityColor = fruityColorAt(fruityPlace());
        // Only a ticker: the color comes from the app-wide clock, not from this animator.
        fruity = ValueAnimator.ofFloat(0, 1);
        fruity.setDuration(FRUITY_LOOP_MILLIS);
        fruity.setRepeatCount(ValueAnimator.INFINITE);
        fruity.addUpdateListener(animation -> {
            fruityColor = fruityColorAt(fruityPlace());
            paintAll();
        });
        fruity.start();
    }

    private boolean restoreAllTitles() {
        restoreTitles(root);
        for (View popup : popups) {
            restoreTitles(popup);
        }
        return true;
    }

    /** Gives any title that has lost its letter colors its colors back. */
    private void restoreTitles(View view) {
        if (view instanceof TextView && shadesLetters((TextView) view)) {
            spreadAcrossLetters((TextView) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                restoreTitles(group.getChildAt(i));
            }
        }
    }

    /** Whether the letters of {@code view} are shaded: titles, and, for Gold, all the accent text and button labels. */
    private boolean shadesLetters(TextView view) {
        Looks looks = looksOf(view);
        if (looks == NOTHING || looks.text == null) {
            // Not accent text (a radio button's label, say, which keeps its own color).
            return false;
        }
        if (view instanceof MaterialButton) {
            // A filled button's label is dark, not the accent; only labels that wear the accent shade.
            return shadeAllText && looks.text != null && wears(looks.text, main);
        }
        return looks.title || shadeAllText;
    }

    private void paintAll() {
        paint(root);
        for (View popup : popups) {
            paintPopup(popup);
        }
    }

    /** Paints a popup: with Fruity running it is gray, since a popup is only briefly open and can't follow the live color. */
    private void paintPopup(View popup) {
        inPopup = true;
        paint(popup);
        inPopup = false;
    }

    /** The color Fruity currently gives the accent parts of what is being painted. */
    private int liveColor() {
        return inPopup ? POPUP_GRAY : fruityColor;
    }

    private void paint(View view) {
        Looks looks = looksOf(view);
        if (looks != NOTHING) {
            if (view instanceof MaterialButton) {
                paintButton((MaterialButton) view, looks);
            } else if (looks.check) {
                // Fruity's selectors are gray, like its popups, rather than following the live color.
                int color = fruity == null ? pair[0] : POPUP_GRAY;
                ((CheckedTextView) view).setCheckMarkTintList(new ColorStateList(
                        new int[][] {{android.R.attr.state_checked}, {}}, new int[] {color, UNCHECKED_GRAY}));
            } else if (view instanceof CompoundButton) {
                // Fruity's selectors are gray, like its popups, rather than following the live color.
                int color = fruity == null ? pair[0] : POPUP_GRAY;
                int unchecked = looks.fill == null ? UNCHECKED_GRAY
                        : looks.fill.getColorForState(new int[0], UNCHECKED_GRAY);
                ((CompoundButton) view).setButtonTintList(new ColorStateList(
                        new int[][] {{android.R.attr.state_checked}, {}}, new int[] {color, unchecked}));
            } else if (view instanceof TextView) {
                paintText((TextView) view, looks);
            } else if (view instanceof ImageView && looks.icon) {
                ((ImageView) view).setColorFilter(fruity == null ? pair[1] : liveColor());
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                paint(group.getChildAt(i));
            }
        }
    }

    /**
     * What {@code view} looked like when first seen (kept, since later paints change it), or NOTHING
     * if it wears no accent.
     */
    private Looks looksOf(View view) {
        Object known = view.getTag(R.id.accent_role);
        if (known != null) {
            return (Looks) known;
        }
        Looks looks = NOTHING;
        if (view instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) view;
            ColorStateList fill = button.getBackgroundTintList();
            // A stroke the screen colors itself (such as the Filter button's) is left to the screen.
            ColorStateList stroke = button.getStrokeWidth() > 0 && wears(button.getStrokeColor(), main, outline)
                    ? button.getStrokeColor() : null;
            ColorStateList label = button.getTextColors();
            if (wears(fill, main) || stroke != null || wears(label, main)) {
                looks = new Looks(fill, stroke, label, false, false, false);
            }
        } else if (view instanceof CompoundButton) {
            ColorStateList tint = ((CompoundButton) view).getButtonTintList();
            // Radio buttons and checkboxes show the accent when checked; their tint comes from the theme.
            looks = new Looks(tint, null, null, false, false, false);
        } else if (view instanceof CheckedTextView) {
            looks = new Looks(null, null, null, false, false, true);
        } else if (view instanceof TextView) {
            TextView label = (TextView) view;
            if (wears(label.getTextColors(), text)) {
                boolean title = label.getTextSize() >= TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, TITLE_SP, label.getResources().getDisplayMetrics());
                looks = new Looks(null, null, label.getTextColors(), title, false, false);
            }
        } else if (view instanceof ImageView && insideTopBar(view)) {
            looks = new Looks(null, null, null, false, true, false);
        }
        view.setTag(R.id.accent_role, looks);
        return looks;
    }

    /** Whether any of the looks of {@code colors} (usual, selected, disabled) is one of {@code accents}. */
    private static boolean wears(ColorStateList colors, int... accents) {
        if (colors == null) {
            return false;
        }
        for (int[] state : LOOKS) {
            int color = colors.getColorForState(state, colors.getDefaultColor());
            for (int accent : accents) {
                if (color == accent) {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code original} with every look that was an accent now {@code color}, and the others kept. */
    private static ColorStateList recolored(ColorStateList original, int color, int... accents) {
        int[] colors = new int[LOOKS.length];
        for (int i = 0; i < LOOKS.length; i++) {
            colors[i] = original.getColorForState(LOOKS[i], original.getDefaultColor());
            for (int accent : accents) {
                if (colors[i] == accent) {
                    colors[i] = color;
                }
            }
        }
        return new ColorStateList(LOOKS, colors);
    }

    /**
     * Gives a filled button Gold's shine: its fill is made see-through and a gold, whitish yellow, gold
     * gradient is put behind it, in the button's own shape, so its stroke, press flash and look when
     * disabled all stay as they were. A button that is filled only while selected (the on and off
     * buttons) shows the shine only then. Done once per button.
     */
    private boolean addShine(MaterialButton button, Looks looks) {
        // Wait until the button has a size (the next layout calls this again), so its corners can be matched.
        if (button.getTag(R.id.gold_shine) != null || button.getWidth() == 0 || button.getHeight() == 0) {
            return button.getTag(R.id.gold_shine) != null;
        }
        boolean always = looks.fill.getColorForState(USUAL, 0) == main;
        // These are set first, while the button still has its own background: afterwards they are not applied.
        button.setBackgroundTintList(recolored(looks.fill, Color.TRANSPARENT, main));
        if (looks.stroke != null) {
            button.setStrokeColor(recolored(looks.stroke, pair[0], main, outline));
        }
        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {titlePair[0], titlePair[1], titlePair[0]});
        // A button with no corner radius of its own is fully rounded, like the buttons that have one this big.
        float half = Math.min(button.getWidth(), button.getHeight()) / 2f;
        float corner = button.getCornerRadius() > 0 ? Math.min(button.getCornerRadius(), half) : half;
        shine.setCornerRadius(corner);
        StateListDrawable shown = new StateListDrawable();
        shown.addState(new int[] {-ENABLED}, new ColorDrawable(Color.TRANSPARENT));
        if (!always) {
            shown.addState(SELECTED, shine);
            shown.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
        } else {
            shown.addState(new int[0], shine);
        }
        InsetDrawable inset = new InsetDrawable(shown, 0, button.getInsetTop(), 0, button.getInsetBottom());
        button.setBackground(new LayerDrawable(new Drawable[] {inset, button.getBackground()}));
        // Nothing is left tinting the new background (the tint set above went to the button's own fill).
        ViewCompat.setBackgroundTintList(button, null);
        button.setTag(R.id.gold_shine, Boolean.TRUE);
        return true;
    }

    private void paintButton(MaterialButton button, Looks looks) {
        int color = fruity == null ? pair[0] : liveColor();
        // Popup buttons (such as the clock's OK and Cancel) are text only, so they get no shine behind them.
        if (shadeAllText && !inPopup && looks.fill != null && wears(looks.fill, main)) {
            if (addShine(button, looks)) {
                button.setTextColor(recolored(looks.text, color, main));
                return;
            }
        }
        if (looks.fill != null) {
            button.setBackgroundTintList(recolored(looks.fill, color, main));
        }
        if (looks.stroke != null) {
            button.setStrokeColor(recolored(looks.stroke, color, main, outline));
        }
        button.setTextColor(recolored(looks.text, color, main));
        if (shadesLetters(button)) {
            spreadAcrossLetters(button);
        }
    }

    private void paintText(TextView view, Looks looks) {
        if (fruity != null) {
            view.setTextColor(recolored(looks.text, liveColor(), text));
        } else if (!looks.title && !shadeAllText) {
            view.setTextColor(recolored(looks.text, pair[1], text));
        } else {
            spreadAcrossLetters(view);
        }
    }

    private static boolean insideTopBar(View view) {
        for (ViewParent parent = view.getParent(); parent instanceof View; parent = parent.getParent()) {
            if (((View) parent).getId() == androidx.appcompat.R.id.action_bar) {
                return true;
            }
        }
        return false;
    }

    /** Colors a title's letters from the first color to the second in the middle and back again (or, for Christmas, in turns). */
    private void spreadAcrossLetters(TextView view) {
        CharSequence shown = view.getText();
        if (shown == null || shown.length() == 0) {
            return;
        }
        // A title that is rebound (such as a day page coming back into view) loses its letter colors.
        if (shown instanceof Spanned
                && ((Spanned) shown).getSpans(0, shown.length(), ForegroundColorSpan.class).length > 0) {
            return;
        }
        String plain = shown.toString();
        SpannableString letters = new SpannableString(plain);
        int letterNumber = 0;
        for (int i = 0; i < plain.length(); i++) {
            int color;
            if (candyCane) {
                // Spaces don't count, so the stripes keep alternating across words.
                color = alternatingColor(letterNumber, candyCaneColors[0], candyCaneColors[1]);
                if (!Character.isWhitespace(plain.charAt(i))) {
                    letterNumber++;
                }
            } else {
                color = letterColor(i, plain.length(), titlePair[0], titlePair[1]);
            }
            letters.setSpan(new ForegroundColorSpan(color), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        view.setText(letters);
    }

    /**
     * The color of letter {@code index} of {@code length}: {@code first} at both ends of the
     * title, {@code second} in its middle, and a blend between.
     */
    static int letterColor(int index, int length, int first, int second) {
        // A word of two or three letters has no true middle, so its letters are spaced evenly inside the title
        // instead, which keeps every one of them at least a little way toward the second color.
        float along = length <= 1 ? 0
                : length <= 3 ? (index + 0.5f) / length
                : index / (float) (length - 1);
        float toSecond = 1 - Math.abs(2 * along - 1);
        return blend(first, second, toSecond);
    }

    /** Letter number {@code letterNumber} (from 0) of a title whose letters take turns: first, second, first... */
    static int alternatingColor(int letterNumber, int first, int second) {
        return letterNumber % 2 == 0 ? first : second;
    }

    static int blend(int from, int to, float fraction) {
        int result = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int a = (from >> shift) & 0xFF;
            int b = (to >> shift) & 0xFF;
            result |= Math.round(a + (b - a) * fraction) << shift;
        }
        return result;
    }
}
