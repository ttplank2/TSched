package edu.illinois.ttplank2.tsched;

import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import java.io.IOException;
import java.util.ArrayList;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

/** Form for adding one fixed or free event, or editing one passed in {@link #EXTRA_EVENT_ID}. */
public class AddEventActivity extends FeedbackActivity {
    /** How long the keyboard takes to slide away once hidden, with a little to spare. */
    private static final long KEYBOARD_HIDE_MILLIS = 450;

    /** Put on an intent to open the form and start its guided tour. */
    public static final String EXTRA_START_TOUR = "edu.illinois.ttplank2.tsched.START_ADD_TOUR";

    /** The id of a saved event to edit; leave it out to add a new event. */
    public static final String EXTRA_EVENT_ID = "edu.illinois.ttplank2.tsched.EVENT_ID";
    /** Longest length the hours wheel offers for a free event. */
    private static final int MAX_HOURS = 12;
    /** The minutes wheel moves in steps of this many minutes. */
    private static final int MINUTE_STEP = 5;
    /** The ids of the weekday buttons, Sunday to Saturday: the days an event is made on. */
    private static final int[] DAY_BUTTON_IDS = {
        R.id.day_sunday, R.id.day_monday, R.id.day_tuesday, R.id.day_wednesday,
        R.id.day_thursday, R.id.day_friday, R.id.day_saturday
    };

    private RadioGroup kindGroup;
    private EditText nameInput;
    private EditText locationInput;
    private View fixedSection;
    private View freeSection;
    private View countSection;
    private Slider countSlider;
    private TextView countValue;
    /** The count the edited event had when the form opened, so an unchanged count isn't reset. */
    private int loadedCount = -1;
    private final MaterialButton[] dayButtons = new MaterialButton[WeekDays.SUNDAY_FIRST.length];
    private Button startButton;
    private Button endButton;
    private NumberPicker hoursPicker;
    private NumberPicker minutesPicker;
    private TextView errorText;
    private MaterialButton colorButton;
    /** When this screen last hid the keyboard, on the {@link SystemClock#uptimeMillis} clock. */
    private long keyboardHiddenAt = -KEYBOARD_HIDE_MILLIS;
    /** The color a new event starts with and the form marks "(Default)": the app's accent color. */
    private int defaultColor = EventColors.DEFAULT;
    private int chosenColor = EventColors.DEFAULT;
    private ColorNames colorNames;

    private int startMinute = Event.NO_TIME;
    private int endMinute = Event.NO_TIME;
    /** The id of the event being edited, or null when adding a new one. */
    private String editingId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_event);
        setTitle(R.string.add_event);

        kindGroup = findViewById(R.id.kind_group);
        nameInput = findViewById(R.id.name_input);
        locationInput = findViewById(R.id.location_input);
        fixedSection = findViewById(R.id.fixed_section);
        freeSection = findViewById(R.id.free_section);
        countSection = findViewById(R.id.count_section);
        countValue = findViewById(R.id.count_value);
        countSlider = findViewById(R.id.count_slider);
        countSlider.addOnChangeListener((slider, value, fromUser) -> {
            showCount();
            if (fromUser) {
                Feedback.tick(this);
            }
        });
        showCount();
        setUpDayButtons(DAY_BUTTON_IDS, dayButtons);
        startButton = findViewById(R.id.start_button);
        endButton = findViewById(R.id.end_button);
        hoursPicker = findViewById(R.id.hours_picker);
        minutesPicker = findViewById(R.id.minutes_picker);
        setUpLengthPickers();
        errorText = findViewById(R.id.error_text);
        colorButton = findViewById(R.id.color_button);
        // Its colors are the event's chosen color, so a secret accent must not recolor them.
        AccentPalette.leaveAlone(colorButton);
        colorNames = new ColorNames(this);
        colorButton.setOnClickListener(view -> afterKeyboardHidden(
                () -> ColorWheelDialog.show(this, R.string.pick_color,
                        chosenColor, this::showColor, () -> showColor(chosenColor))));
        defaultColor = new AccentSetting(this).load();
        chosenColor = defaultColor;
        showColor(chosenColor);

        kindGroup.setOnCheckedChangeListener((group, checkedId) -> showSectionForKind());

        startButton.setOnClickListener(view -> pickTime(startButton,
                startMinute == Event.NO_TIME ? 9 * 60 : startMinute, minute -> startMinute = minute));
        endButton.setOnClickListener(view -> pickTime(endButton,
                endMinute == Event.NO_TIME ? 10 * 60 : endMinute, minute -> endMinute = minute));

        findViewById(R.id.save_button).setOnClickListener(view -> save());
        showSectionForKind();

        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_START_TOUR, false)) {
            startTour(TourKind.ADD, 0);
        }

        editingId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (editingId != null) {
            setTitle(R.string.edit_event);
            if (savedInstanceState == null) {
                fillFromSavedEvent();
            }
        }
    }

    /** A touch anywhere but on the text field being edited ends the editing, like tapping away. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && getCurrentFocus() instanceof EditText) {
            View field = getCurrentFocus();
            Rect bounds = new Rect();
            field.getGlobalVisibleRect(bounds);
            if (!bounds.contains((int) event.getRawX(), (int) event.getRawY())) {
                InputMethodManager keyboard = getSystemService(InputMethodManager.class);
                keyboard.hideSoftInputFromWindow(field.getWindowToken(), 0);
                keyboardHiddenAt = SystemClock.uptimeMillis();
                // Focus has to land somewhere: the form itself, not the next text field.
                findViewById(R.id.form_root).requestFocus();
            }
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Runs {@code action} once the keyboard, if this screen just hid it, has finished sliding
     * away. A popup opened mid-slide makes the system bring the keyboard back for a moment,
     * which looks like a flash.
     */
    private void afterKeyboardHidden(Runnable action) {
        long wait = KEYBOARD_HIDE_MILLIS - (SystemClock.uptimeMillis() - keyboardHiddenAt);
        if (wait > 0) {
            findViewById(R.id.form_root).postDelayed(action, wait);
        } else {
            action.run();
        }
    }

    /** The tour ends, finished or skipped, by going back to the calendar; nothing was saved. */
    @Override
    protected void onTourFinished(TourKind kind, boolean completed) {
        finish();
    }

    private void showSectionForKind() {
        int kind = kindGroup.getCheckedRadioButtonId();
        boolean free = kind == R.id.kind_free;
        // Fixed and counted events share the day and time section; counted adds a count.
        fixedSection.setVisibility(free ? View.GONE : View.VISIBLE);
        freeSection.setVisibility(free ? View.VISIBLE : View.GONE);
        countSection.setVisibility(kind == R.id.kind_counted ? View.VISIBLE : View.GONE);
        errorText.setVisibility(View.GONE);
    }

    private int chosenCount() {
        return (int) countSlider.getValue();
    }

    private void showCount() {
        countValue.setText(String.valueOf(chosenCount()));
    }

    /** The end time, marked "(next day)" when it is at or before the start, so the event runs overnight. */
    private void showEndTime() {
        if (endMinute == Event.NO_TIME) {
            return;
        }
        String label = TimeFormat.clock(endMinute);
        if (startMinute != Event.NO_TIME && endMinute < startMinute) {
            label += " " + getString(R.string.next_day_suffix);
        }
        endButton.setText(label);
    }

    /** Opens the clock on {@code openingMinute}: the time already chosen, or a suggestion if none is. */
    private void pickTime(Button button, int openingMinute, IntConsumer onPicked) {
        TimePickerDialog dialog = new TimePickerDialog(AccentPalette.popupContext(this), (picker, hour, minute) -> {
            int minuteOfDay = hour * 60 + minute;
            onPicked.accept(minuteOfDay);
            button.setText(TimeFormat.clock(minuteOfDay));
            button.setSelected(true);
            showEndTime();
        }, openingMinute / 60, openingMinute % 60, false);
        Feedback.watchTaps(dialog.getWindow());
        AccentPalette.watchDialog(dialog);
        dialog.show();
        Feedback.tickOnTimeChanges(dialog);
    }

    private void save() {
        Event event;
        try {
            event = buildEvent().withColor(chosenColor);
        } catch (IllegalArgumentException e) {
            errorText.setText(e.getMessage());
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        EventStore store = new EventStore(this);
        boolean lostPlacement = false;
        Set<DayOfWeek> days = selectedDays(dayButtons);
        List<FreeEventOverlaps.Overlap> overlaps = new ArrayList<>();
        try {
            // One event per chosen day, grouped so that editing one edits them all.
            List<Event> before = store.load();
            List<Event> after;
            if (event.getKind() == Event.Kind.FREE) {
                after = EventGroups.saveFree(before, event, days);
                lostPlacement = lostPlacement(before, after);
            } else {
                // A counted event keeps the count each day has left unless the count was changed.
                boolean keepCounts = loadedCount == chosenCount();
                after = EventGroups.saveFixed(before, event, days, keepCounts);
            }
            store.save(after);
            if (event.getKind() != Event.Kind.FREE) {
                // The events that are new or changed may now sit on a placed free event.
                List<Event> changed = new ArrayList<>();
                for (Event saved : after) {
                    if (!before.contains(saved)) {
                        changed.add(saved);
                    }
                }
                overlaps = FreeEventOverlaps.find(after, changed);
            }
        } catch (IOException e) {
            errorText.setText(R.string.save_failed);
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        if (event.getKind() != Event.Kind.FREE) {
            // The calendar shows its Free Event Overlap popup when it is next on screen.
            OverlapNotice.leave(overlaps);
        }
        String message = lostPlacement
                ? getString(R.string.saved_and_unplaced, event.getName())
                : days.size() > 1
                ? getResources().getQuantityString(R.plurals.saved_on_days, days.size(),
                        event.getName(), days.size())
                : getString(R.string.saved, event.getName());
        Toast.makeText(this, message, lostPlacement ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        finish();
    }

    /** Whether a free event that was placed before is back in Unplaced Free Events after saving. */
    private static boolean lostPlacement(List<Event> before, List<Event> after) {
        for (Event old : before) {
            if (!old.isPlaced()) {
                continue;
            }
            for (Event saved : after) {
                if (saved.getId().equals(old.getId()) && !saved.isPlaced()) {
                    return true;
                }
            }
        }
        return false;
    }

    private Event buildEvent() {
        String name = nameInput.getText().toString();
        String location = locationInput.getText().toString();
        int kind = kindGroup.getCheckedRadioButtonId();
        Set<DayOfWeek> days = selectedDays(dayButtons);
        if (kind != R.id.kind_free) {
            if (startMinute == Event.NO_TIME || endMinute == Event.NO_TIME) {
                throw new IllegalArgumentException(getString(R.string.pick_both_times));
            }
            if (days.isEmpty()) {
                throw new IllegalArgumentException(getString(R.string.pick_a_day));
            }
            if (endMinute == startMinute) {
                throw new IllegalArgumentException(getString(R.string.start_end_same));
            }
            // An end time before the start means the event ends the next day.
            int duration = Math.floorMod(endMinute - startMinute, Event.MINUTES_PER_DAY);
            Event.requireMinimumLength(duration);
            // The day here is a stand-in: EventGroups saves a copy on every chosen day.
            DayOfWeek day = days.iterator().next();
            if (kind == R.id.kind_counted) {
                return Event.countedWithId(newOrEditedId(), name, location, day, startMinute,
                        duration, chosenCount());
            }
            return Event.fixedWithId(newOrEditedId(), name, location, day, startMinute, duration);
        }
        if (days.isEmpty()) {
            throw new IllegalArgumentException(getString(R.string.pick_a_day));
        }
        int length = hoursPicker.getValue() * 60 + minutesPicker.getValue() * MINUTE_STEP;
        Event.requireMinimumLength(length);
        // The days here are stand-ins: EventGroups saves a copy on every chosen day.
        return Event.freeWithId(newOrEditedId(), name, location, length, days);
    }

    /** Finds a row of weekday buttons and makes each a toggle with its full day name for TalkBack. */
    private void setUpDayButtons(int[] ids, MaterialButton[] buttons) {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = findViewById(ids[i]);
            buttons[i].setCheckable(true);
            buttons[i].setContentDescription(
                    WeekDays.SUNDAY_FIRST[i].getDisplayName(TextStyle.FULL, Locale.getDefault()));
        }
    }

    /** The days whose buttons are chosen in a row. */
    private static Set<DayOfWeek> selectedDays(MaterialButton[] buttons) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i].isChecked()) {
                days.add(WeekDays.SUNDAY_FIRST[i]);
            }
        }
        return days;
    }

    /** Chooses exactly the buttons in a row for {@code days}. */
    private static void selectDays(MaterialButton[] buttons, Set<DayOfWeek> days) {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setChecked(days.contains(WeekDays.SUNDAY_FIRST[i]));
        }
    }

    /** Shows {@code color} on the Color button as its outline and label. */
    private void showColor(int color) {
        chosenColor = color;
        String name = colorNames.nameOf(color);
        if (color == defaultColor) {
            name = getString(R.string.color_default_named, name);
        }
        colorButton.setText(name);
        colorButton.setTextColor(color);
        colorButton.setStrokeColor(ColorStateList.valueOf(color));
    }

    private String newOrEditedId() {
        return editingId != null ? editingId : UUID.randomUUID().toString();
    }

    /** Puts the edited event's current values into the form. */
    private void fillFromSavedEvent() {
        Event event = null;
        List<Event> saved = List.of();
        try {
            saved = new EventStore(this).load();
        } catch (IOException ignored) {
            // Handled below: with nothing loaded, the event counts as missing.
        }
        for (Event candidate : saved) {
            if (candidate.getId().equals(editingId)) {
                event = candidate;
            }
        }
        if (event == null) {
            Toast.makeText(this, R.string.edit_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        nameInput.setText(event.getName());
        showColor(event.getColor());
        locationInput.setText(event.getLocation());
        selectDays(dayButtons, daysOf(EventGroups.membersOf(saved, event)));
        if (event.getKind() != Event.Kind.FREE) {
            if (event.getKind() == Event.Kind.COUNTED) {
                kindGroup.check(R.id.kind_counted);
                loadedCount = event.getCount();
                countSlider.setValue(loadedCount);
            } else {
                kindGroup.check(R.id.kind_fixed);
            }
            startMinute = event.getStartMinute();
            endMinute = event.getEndMinute() % Event.MINUTES_PER_DAY;
            startButton.setText(TimeFormat.clock(startMinute));
            showEndTime();
            startButton.setSelected(true);
            endButton.setSelected(true);
        } else {
            kindGroup.check(R.id.kind_free);
            hoursPicker.setValue(Math.min(MAX_HOURS, event.getDurationMinutes() / 60));
            minutesPicker.setValue(event.getDurationMinutes() % 60 / MINUTE_STEP);
        }
    }

    /**
     * The days a group of events is on: each fixed or placed event's own day, and for an unplaced
     * free event the days it is limited to.
     */
    private static Set<DayOfWeek> daysOf(List<Event> members) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (Event member : members) {
            if (member.getDay() != null) {
                days.add(member.getDay());
            } else {
                days.addAll(member.getAllowedDays());
            }
        }
        return days;
    }

    private void setUpLengthPickers() {
        hoursPicker.setMinValue(0);
        hoursPicker.setMaxValue(MAX_HOURS);
        hoursPicker.setValue(1);

        String[] minuteLabels = new String[60 / MINUTE_STEP];
        for (int i = 0; i < minuteLabels.length; i++) {
            minuteLabels[i] = String.format(Locale.US, "%02d", i * MINUTE_STEP);
        }
        minutesPicker.setMinValue(0);
        minutesPicker.setMaxValue(minuteLabels.length - 1);
        minutesPicker.setDisplayedValues(minuteLabels);
        minutesPicker.setValue(0);
    }
}
