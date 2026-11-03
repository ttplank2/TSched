package edu.illinois.ttplank2.tsched;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.TimePicker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/**
 * The little vibration and click sound for presses and for each stop of a wheel, when Settings has
 * them on. Anything smooth (scrolling, swiping between days) is left out: only a tap on something
 * pressable, and a wheel or slider landing on a new value, call {@link #tick}.
 */
public final class Feedback {
    private static final int SOUND_RATE_HZ = 22050;
    private static final int SOUND_MILLIS = 25;
    private static final double SOUND_PITCH_HZ = 1800;
    private static final double SOUND_FADE_PER_SECOND = 180;
    private static final double SOUND_LOUDNESS = 0.2;
    private static final int FALLBACK_VIBRATION_MILLIS = 15;

    private static boolean loaded;
    private static boolean vibrationOn;
    private static boolean soundOn;
    private static AudioTrack clickTrack;

    private Feedback() {
    }

    /** Makes the switches in Settings take effect right away. */
    public static synchronized void setSwitches(boolean vibration, boolean sound) {
        loaded = true;
        vibrationOn = vibration;
        soundOn = sound;
    }

    /** Vibrates and/or clicks, as Settings says. Does nothing when both are off. */
    public static void tick(Context context) {
        tick(context, true);
    }

    /** A wheel turning: vibrates if Settings says so, but never makes the click sound. */
    public static void tickWithoutSound(Context context) {
        tick(context, false);
    }

    private static void tick(Context context, boolean withSound) {
        boolean vibrate;
        boolean click;
        synchronized (Feedback.class) {
            if (!loaded) {
                boolean[] saved = new FeedbackSettings(context.getApplicationContext()).load();
                setSwitches(saved[0], saved[1]);
            }
            vibrate = vibrationOn;
            click = withSound && soundOn;
        }
        if (vibrate) {
            vibrate(context);
        }
        if (click) {
            playClick();
        }
    }

    private static void vibrate(Context context) {
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(
                    FALLBACK_VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    /** A short, soft click made from a fading tone, so no sound file is needed. */
    private static synchronized void playClick() {
        try {
            if (clickTrack == null) {
                clickTrack = buildClick();
            } else {
                clickTrack.stop();
                clickTrack.reloadStaticData();
            }
            clickTrack.play();
        } catch (IllegalStateException e) {
            // The audio system wasn't ready; this click is skipped and the next one starts fresh.
            if (clickTrack != null) {
                clickTrack.release();
                clickTrack = null;
            }
        }
    }

    private static AudioTrack buildClick() {
        int samples = SOUND_RATE_HZ * SOUND_MILLIS / 1000;
        short[] pcm = new short[samples];
        for (int i = 0; i < samples; i++) {
            double seconds = i / (double) SOUND_RATE_HZ;
            double wave = Math.sin(2 * Math.PI * SOUND_PITCH_HZ * seconds)
                    * Math.exp(-SOUND_FADE_PER_SECOND * seconds);
            pcm[i] = (short) (wave * SOUND_LOUDNESS * Short.MAX_VALUE);
        }
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SOUND_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(samples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        track.write(pcm, 0, samples);
        return track;
    }

    /**
     * Makes every tap on a pressable thing in this window tick, once the tap is done. Call it
     * before the window is shown. A tap is a touch that barely moves and isn't held, so scrolling,
     * swiping and long presses stay silent.
     */
    public static void watchTaps(Window window) {
        Window.Callback original = window.getCallback();
        if (original == null) {
            return;
        }
        TapWatcher watcher = new TapWatcher(window);
        window.setCallback((Window.Callback) Proxy.newProxyInstance(
                Window.Callback.class.getClassLoader(), new Class<?>[] {Window.Callback.class},
                (proxy, method, args) -> {
                    boolean touch = method.getName().equals("dispatchTouchEvent");
                    boolean tickAfter = touch && watcher.isPressableTap((MotionEvent) args[0]);
                    Object result;
                    try {
                        result = method.invoke(original, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (tickAfter) {
                        tick(window.getContext());
                    }
                    return result;
                }));
    }

    /**
     * Ticks each time the hour or minute changes in a time popup that is already showing, as
     * the user drags the clock or turns its wheels.
     */
    public static void tickOnTimeChanges(Dialog shownDialog) {
        TimePicker picker = findTimePicker(shownDialog.getWindow().getDecorView());
        if (picker != null) {
            Context context = shownDialog.getContext();
            picker.setOnTimeChangedListener((view, hour, minute) -> tickWithoutSound(context));
        }
    }

    private static TimePicker findTimePicker(View view) {
        if (view instanceof TimePicker) {
            return (TimePicker) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TimePicker found = findTimePicker(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Follows touches to tell when one finishes as a tap on something pressable. */
    private static final class TapWatcher {
        private final Window window;
        private final int touchSlop;
        private float downX;
        private float downY;
        private long downTime;
        private boolean moved;

        TapWatcher(Window window) {
            this.window = window;
            touchSlop = ViewConfiguration.get(window.getContext()).getScaledTouchSlop();
        }

        boolean isPressableTap(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    downTime = event.getEventTime();
                    moved = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getX() - downX) > touchSlop
                            || Math.abs(event.getY() - downY) > touchSlop) {
                        moved = true;
                    }
                    return false;
                case MotionEvent.ACTION_POINTER_DOWN:
                    moved = true;
                    return false;
                case MotionEvent.ACTION_UP:
                    boolean quick =
                            event.getEventTime() - downTime < ViewConfiguration.getLongPressTimeout();
                    return !moved && quick && isPressable(window.getDecorView(), downX, downY);
                default:
                    return false;
            }
        }
    }

    /** Whether the deepest view at this spot in the window, or one around it, can be pressed. */
    private static boolean isPressable(View root, float windowX, float windowY) {
        View view = deepestViewAt(root, (int) windowX, (int) windowY);
        while (view != null) {
            if (view instanceof EditText) {
                return false;
            }
            if (view.isClickable() && view.isEnabled()) {
                return true;
            }
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return false;
    }

    // Positions are inside the window, which for a popup is not the same as on the screen.
    private static View deepestViewAt(View view, int windowX, int windowY) {
        Rect bounds = new Rect();
        if (view.getVisibility() != View.VISIBLE
                || !view.getGlobalVisibleRect(bounds) || !bounds.contains(windowX, windowY)) {
            return null;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = deepestViewAt(group.getChildAt(i), windowX, windowY);
                if (child != null) {
                    return child;
                }
            }
        }
        return view;
    }
}
