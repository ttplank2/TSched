package edu.illinois.ttplank2.tsched;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import java.util.Set;

/** The Settings screen, reached from the gear in the calendar's top bar. */
public class SettingsActivity extends FeedbackActivity {
    /** The file that keeps whether today's calendar view starts three hours before the current time. */
    static final String TODAY_AT_NOW_FILE = "today_at_now.txt";

    private NotificationSettings notificationSettings;
    private Button notificationsButton;
    private Button notifyLeadButton;
    private Button notifyColorsButton;
    /** Asks Android for permission to show notifications, which the user is asked for when turning them on. */
    private final ActivityResultLauncher<String> askToNotify = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    switchNotifications(true);
                } else {
                    Toast.makeText(this, R.string.notifications_blocked, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings);
        findViewById(R.id.delete_many_button).setOnClickListener(
                view -> startActivity(new Intent(this, DeleteManyActivity.class)));

        // The tour is on the calendar, so this goes back to it and asks it to start.
        findViewById(R.id.replay_tutorial_button).setOnClickListener(view -> startActivity(
                new Intent(this, MainActivity.class)
                        .putExtra(MainActivity.EXTRA_START_TOUR, true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)));
        findViewById(R.id.accent_color_button).setOnClickListener(view -> ColorWheelDialog.showAccent(
                this, R.string.accent_color, new AccentSetting(this).load(),
                color -> saveAccent(new AccentSetting(this).save(color)),
                secret -> {
                    AccentPalette.restartFruity();
                    saveAccent(new AccentSetting(this).saveSecret(AccentColors.SECRET_KEYS[secret]));
                }));
        setUpStartTime();
        setUpTodayAtNow();
        setUpFeedbackSwitches();
        setUpNotifications();

        VerticalLock verticalLock = new VerticalLock(this);
        Button lockButton = findViewById(R.id.lock_vertical_button);
        showOn(lockButton, verticalLock.isLocked());
        lockButton.setOnClickListener(view -> {
            boolean locked = !lockButton.isSelected();
            if (verticalLock.setLocked(locked)) {
                showOn(lockButton, locked);
            } else {
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** The accent is part of the theme, so after it is saved this screen is rebuilt with the new one. */
    private void saveAccent(boolean saved) {
        if (saved) {
            recreate();
        } else {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** Wires the button that shows the time the calendar starts at and opens a clock to change it. */
    private void setUpStartTime() {
        StartTime startTime = new StartTime(this);
        Button button = findViewById(R.id.start_time_button);
        showStartTime(button, startTime.load());
        button.setOnClickListener(view -> {
            int current = startTime.load();
            TimePickerDialog dialog = new TimePickerDialog(AccentPalette.popupContext(this), (picker, hour, minute) -> {
                int chosen = hour * 60 + minute;
                if (startTime.save(chosen)) {
                    showStartTime(button, chosen);
                } else {
                    Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
                }
            }, current / 60, current % 60, false);
            Feedback.watchTaps(dialog.getWindow());
            AccentPalette.watchDialog(dialog);
            dialog.show();
            Feedback.tickOnTimeChanges(dialog);
        });
    }

    /** Wires the switch that starts today's view three hours before the current time. */
    private void setUpTodayAtNow() {
        OnOffSetting todayAtNow = new OnOffSetting(this, TODAY_AT_NOW_FILE);
        Button button = findViewById(R.id.start_today_at_now_button);
        showOn(button, todayAtNow.isOn());
        button.setOnClickListener(view -> {
            boolean on = !button.isSelected();
            if (todayAtNow.setOn(on)) {
                showOn(button, on);
            } else {
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Wires the three notification controls: the on and off switch, the number of minutes before an
     * event (one number for every event), and the colors of event that notify (picked on the color wheel).
     */
    private void setUpNotifications() {
        notificationSettings = new NotificationSettings(this);
        notificationsButton = findViewById(R.id.notifications_button);
        notifyLeadButton = findViewById(R.id.notify_lead_button);
        notifyColorsButton = findViewById(R.id.notify_colors_button);
        showNotificationsOn(notificationSettings.isOn() && NotificationScheduler.mayShowNotifications(this));
        showNotifyLead();
        notificationsButton.setOnClickListener(view -> {
            if (notificationsButton.isSelected()) {
                switchNotifications(false);
            } else if (NotificationScheduler.mayShowNotifications(this)) {
                switchNotifications(true);
            } else {
                askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });
        notifyLeadButton.setOnClickListener(view -> askLeadMinutes());
        notifyColorsButton.setOnClickListener(view -> {
            Set<Integer> chosen = notificationSettings.colors();
            ColorWheelDialog.showMulti(this, R.string.notify_colors_title, chosen, color -> {
                if (!chosen.remove(color)) {
                    chosen.add(color);
                }
                if (notificationSettings.saveColors(chosen)) {
                    NotificationScheduler.reschedule(this);
                } else {
                    Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /** Turns notifications on or off, saving it and setting the next notification to match. */
    private void switchNotifications(boolean on) {
        if (notificationSettings.setOn(on)) {
            showNotificationsOn(on);
            NotificationScheduler.reschedule(this);
        } else {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** Shows the round switch filled with a bell while notifications are on, and empty with a crossed-out bell while off. */
    private void showNotificationsOn(boolean on) {
        showOn(notificationsButton, on);
        ((com.google.android.material.button.MaterialButton) notificationsButton).setIconResource(
                on ? R.drawable.ic_notifications_on : R.drawable.ic_notifications_off);
    }

    private void showNotifyLead() {
        int minutes = notificationSettings.leadMinutes();
        notifyLeadButton.setText(minutes == 0
                ? getString(R.string.notify_at_start_button)
                : getString(R.string.notify_before_button,
                        getResources().getQuantityString(R.plurals.minutes_count, minutes, minutes)));
    }

    /** Opens a box to type how many minutes before an event it notifies (0 to 600); a bad number shows an error and stays open. */
    private void askLeadMinutes() {
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setHint(R.string.notify_lead_hint);
        field.setSingleLine(true);
        field.setText(Integer.toString(notificationSettings.leadMinutes()));
        field.selectAll();
        FrameLayout box = new FrameLayout(this);
        int side = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(side, 0, side, 0);
        box.addView(field);
        AlertDialog dialog = new FeedbackDialogBuilder(this)
                .setTitle(R.string.notify_lead_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        // Set after show(), so a bad number does not close the box.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int minutes = parseMinutes(field.getText().toString());
            if (!NotificationSettings.isValidLead(minutes)) {
                field.setError(getString(R.string.notify_lead_error));
            } else if (notificationSettings.saveLeadMinutes(minutes)) {
                showNotifyLead();
                NotificationScheduler.reschedule(this);
                dialog.dismiss();
            } else {
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** The whole number typed, or -1 (which is not a valid number of minutes) if it is empty or too big to read. */
    private static int parseMinutes(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void showStartTime(Button button, int minute) {
        button.setText(getString(R.string.start_calendar_view_at, TimeFormat.clock(minute)));
    }

    /** Wires a Vibration or Sound switch: tapping flips it, saves both, and applies at once. */
    private void setUpFeedbackSwitches() {
        FeedbackSettings settings = new FeedbackSettings(this);
        boolean[] saved = settings.load();
        Button vibration = findViewById(R.id.vibration_button);
        Button sound = findViewById(R.id.sound_button);
        showOn(vibration, saved[0]);
        showOn(sound, saved[1]);
        View.OnClickListener flip = view -> {
            boolean vibrationOn = vibration.isSelected() != (view == vibration);
            boolean soundOn = sound.isSelected() != (view == sound);
            if (settings.save(vibrationOn, soundOn)) {
                showOn(vibration, vibrationOn);
                showOn(sound, soundOn);
                Feedback.setSwitches(vibrationOn, soundOn);
            } else {
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
            }
        };
        vibration.setOnClickListener(flip);
        sound.setOnClickListener(flip);
    }

    /** A filled yellow button has bold text, like every other one in the app. */
    private static void showOn(Button button, boolean on) {
        button.setSelected(on);
        button.setTypeface(button.getTypeface(), on ? Typeface.BOLD : Typeface.NORMAL);
    }
}
