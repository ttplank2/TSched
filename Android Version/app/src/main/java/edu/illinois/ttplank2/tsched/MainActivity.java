package edu.illinois.ttplank2.tsched;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The week as a swipeable calendar: one day per page, with the neighboring days peeking in
 * at the sides, and a button to the board of free events along the bottom.
 */
public class MainActivity extends FeedbackActivity implements DayScheduleView.BlockListener {
    /** How much of the previous and next day shows at each side. */
    private static final int PEEK_DP = 28;
    private static final int PAGE_GAP_DP = 8;
    /** How dark the black shade over the previous and next days gets (0 = none, 1 = black). */
    private static final float SIDE_PAGE_SHADE = 0.5f;
    /** How many times its usual width the Filter button may grow to for a long color name. */
    private static final float FILTER_MAX_WIDTH_FACTOR = 2.6f;
    /** The key under which the chosen filter color is kept while the screen is rebuilt. */
    /** How long the black cover takes to fade away when the app starts. */
    private static final long STARTUP_FADE_MILLIS = 500;
    private static final String STATE_FILTER_COLOR = "filter_color";
    private static final String STATE_TOUR_CHECKED = "tour_checked";
    /** Put on an intent to open the calendar and start its guided tour. */
    public static final String EXTRA_START_TOUR = "edu.illinois.ttplank2.tsched.START_TOUR";
    /** The longest the app waits before checking whether a counted event has ended. */
    private static final long COUNTDOWN_CHECK_LIMIT_MILLIS = 60_000;
    /** A moment past an end time, so the clock has surely reached it when the check runs. */
    private static final long COUNTDOWN_CHECK_SLACK_MILLIS = 1_000;

    private EventStore store;
    private ViewPager2 dayPager;
    private DayPagerAdapter dayAdapter;
    private Button freeEventsButton;
    private TextView errorMessage;
    private int filterColor = ColorWheelDialog.CLEAR;
    /** Whether this screen has already decided about starting the tour by itself. */
    private boolean tourChecked;
    private MaterialButton filterButton;
    /** The whole-week view, shown in wide (landscape) mode instead of the day pager. */
    private WeekView weekView;
    private WeekHeaderView weekHeader;
    /** Whether the wide-mode week has been scrolled to the first events yet. */
    private List<Event> shownEvents = new ArrayList<>();
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final Runnable countdownCheck = this::checkCountdown;
    private ColorNames colorNames;
    /** How wide the Filter button is with its usual label; long color names widen it from there. */
    private int filterButtonBaseWidth;
    /** Tap Filter to pick a color; double-tap to clear the filter. */
    private final TapOrDoubleTap filterTaps =
            new TapOrDoubleTap(this::showFilterWheel, () -> setFilter(ColorWheelDialog.CLEAR));
    /** Tap Snap to Day to pick a day; double-tap to jump to today. */
    private final TapOrDoubleTap snapTaps = new TapOrDoubleTap(this::showSnapToDay, this::snapToToday);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        showScriptTitle();
        store = new EventStore(this);
        colorNames = new ColorNames(this);
        errorMessage = findViewById(R.id.error_message);
        freeEventsButton = findViewById(R.id.free_events_button);
        dayPager = findViewById(R.id.day_pager);
        weekView = findViewById(R.id.week_view);
        weekHeader = findViewById(R.id.week_header);
        weekView.setBlockListener(this);
        setUpDayPager();
        if (savedInstanceState == null) {
            // Only when the app starts: coming back to it, or turning the phone, skips this.
            fadeInFromBlack();
        }
        if (savedInstanceState != null) {
            // Turning the phone rebuilds this screen; the filter chosen before it carries over.
            filterColor = savedInstanceState.getInt(STATE_FILTER_COLOR, ColorWheelDialog.CLEAR);
            tourChecked = savedInstanceState.getBoolean(STATE_TOUR_CHECKED);
            dayAdapter.setFilter(filterColor);
        }
        findViewById(R.id.add_event_button).setOnClickListener(
                view -> startActivity(new Intent(this, AddEventActivity.class)));
        freeEventsButton.setOnClickListener(
                view -> startActivity(new Intent(this, FreeEventsActivity.class)));
        if (savedInstanceState == null) {
            dayPager.setCurrentItem(WeekPages.startPage(LocalDate.now().getDayOfWeek()), false);
        }
    }

    /** A black cover over the whole screen that fades away, so the app fades in as it starts. */
    private void fadeInFromBlack() {
        ViewGroup window = (ViewGroup) getWindow().getDecorView();
        View cover = new View(this);
        cover.setBackgroundColor(Color.BLACK);
        window.addView(cover, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cover.animate().alpha(0f).setDuration(STARTUP_FADE_MILLIS)
                .withEndAction(() -> window.removeView(cover)).start();
    }

    /** The calendar is the first screen, so Back stays here instead of leaving the app. */
    @Override
    protected boolean staysOnBack() {
        return true;
    }

    /** Replaces the plain title with "TSched" in the Pacifico script font. */
    private void showScriptTitle() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setCustomView(R.layout.action_title);
        }
    }

    private void setUpDayPager() {
        dayAdapter = new DayPagerAdapter(this);
        dayPager.setAdapter(dayAdapter);
        dayPager.setOffscreenPageLimit(1);

        float density = getResources().getDisplayMetrics().density;
        int peek = (int) ((PEEK_DP + PAGE_GAP_DP) * density);
        RecyclerView pages = (RecyclerView) dayPager.getChildAt(0);
        pages.setPadding(peek, 0, peek, 0);
        pages.setClipToPadding(false);

        CompositePageTransformer transformer = new CompositePageTransformer();
        transformer.addTransformer(new MarginPageTransformer((int) (PAGE_GAP_DP * density)));
        transformer.addTransformer((page, position) -> {
            float distance = Math.min(1f, Math.abs(position));
            View shade = page.findViewById(R.id.side_shade);
            if (shade != null) {
                shade.setAlpha(distance * SIDE_PAGE_SHADE);
            }
            page.setScaleY(1f - distance * 0.04f);
        });
        dayPager.setPageTransformer(transformer);
    }

    @Override
    protected boolean hasHalloweenSpider() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        NotificationScheduler.reschedule(this);
        refresh();
        startTourIfWanted();
        showOverlapNotice();
    }

    /**
     * Starts the guided tour when Settings asked for it, and by itself when the calendar opens with
     * no events at all, once for each time the calendar is opened. If the events could not be
     * loaded, nothing is assumed.
     */
    private void startTourIfWanted() {
        if (isTourShowing()) {
            return;
        }
        if (getIntent().getBooleanExtra(EXTRA_START_TOUR, false)) {
            getIntent().removeExtra(EXTRA_START_TOUR);
            tourChecked = true;
            startTour(TourKind.HOME, 0);
            return;
        }
        if (tourChecked) {
            return;
        }
        tourChecked = true;
        if (errorMessage.getVisibility() != View.VISIBLE && shownEvents.isEmpty()) {
            startTour(TourKind.HOME, 0);
        }
    }

    /** Settings sends the calendar back to the front, asking for the tour. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    /** The home tour ends by showing how to add an event, on the Add Event screen's own tour. */
    @Override
    protected void onTourFinished(TourKind kind, boolean completed) {
        if (kind == TourKind.HOME && completed) {
            startActivity(new Intent(this, AddEventActivity.class)
                    .putExtra(AddEventActivity.EXTRA_START_TOUR, true));
        }
    }

    /**
     * Shows the "Free Event Overlap" popup if a fixed event was just saved on top of placed free
     * events: it names each overlap and offers to Recall the free events so they can be placed
     * again, or to leave things as they are.
     */
    private void showOverlapNotice() {
        List<FreeEventOverlaps.Overlap> overlaps = OverlapNotice.take();
        if (overlaps.isEmpty()) {
            return;
        }
        StringBuilder lines = new StringBuilder();
        Set<String> freeIds = new HashSet<>();
        for (FreeEventOverlaps.Overlap overlap : overlaps) {
            lines.append(lines.length() == 0 ? "" : "\n\n").append(getString(R.string.overlap_line,
                    overlap.free.getName(), overlap.fixed.getName()));
            freeIds.add(overlap.free.getId());
        }
        new FeedbackDialogBuilder(this)
                .setTitle(R.string.overlap_title)
                .setMessage(lines)
                .setNeutralButton(R.string.overlap_recall, (dialog, which) -> recallFreeEvents(freeIds))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    /** Sends the free events with these ids back to Unplaced Free Events. */
    private void recallFreeEvents(Set<String> ids) {
        try {
            List<Event> events = store.load();
            List<Event> updated = EventGroups.recalled(events, ids);
            int recalled = FreeEventPlacer.countPlaced(events) - FreeEventPlacer.countPlaced(updated);
            store.save(updated);
            Toast.makeText(this, getResources().getQuantityString(R.plurals.recalled_count, recalled,
                    recalled), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    /** Whether the phone is wide, so the whole week is shown instead of one day at a time. */
    private boolean isWide() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void refresh() {
        List<Event> events;
        try {
            events = store.load();
            errorMessage.setVisibility(View.GONE);
        } catch (IOException e) {
            events = new ArrayList<>();
            errorMessage.setVisibility(View.VISIBLE);
        }
        dayAdapter.setStartMinute(new StartTime(this).load());
        dayAdapter.setTodayStartsAtNow(
                new OnOffSetting(this, SettingsActivity.TODAY_AT_NOW_FILE).isOn());
        dayAdapter.setEvents(events);
        shownEvents = events;
        if (isWide()) {
            weekHeader.showToday();
            weekView.setWeek(events, filterColor);
        }
        int freeCount = EventRows.unplacedFree(events).size();
        freeEventsButton.setText(getString(R.string.free_events_button, FreeCountLabel.of(freeCount)));
    }

    /**
     * Counted events lose a count for each end time that passed while the app was closed, and
     * while it stays open they are counted again the moment each one ends.
     */
    @Override
    protected void onStart() {
        super.onStart();
        applyCountdown();
        scheduleCountdownCheck();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_FILTER_COLOR, filterColor);
        outState.putBoolean(STATE_TOUR_CHECKED, tourChecked);
    }

    /**
     * The filter only lasts while the calendar is on screen, but turning the phone keeps it. The
     * time the app closed is kept.
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            setFilter(ColorWheelDialog.CLEAR);
        }
        countdownHandler.removeCallbacks(countdownCheck);
        new LastOpened(this).save(System.currentTimeMillis());
    }

    /**
     * Works out how many end times passed since the app was last open, counts counted events down
     * by that many, and remembers now. If the events can't be saved, the old time is kept so the
     * next visit tries again.
     */
    private boolean applyCountdown() {
        LastOpened lastOpened = new LastOpened(this);
        long last = lastOpened.load();
        long now = System.currentTimeMillis();
        boolean changed = false;
        if (last >= 0) {
            try {
                List<Event> events = store.load();
                List<Event> counted = CountedEvents.advance(events, toLocalTime(last), toLocalTime(now));
                if (!counted.equals(events)) {
                    store.save(counted);
                    changed = true;
                }
            } catch (IOException e) {
                return false;
            }
        }
        lastOpened.save(now);
        return changed;
    }

    /**
     * Wakes the app up just after the next counted event ends (or in a minute, whichever is
     * sooner) to count it down and redraw the calendar, so an event can be watched ending.
     */
    private void scheduleCountdownCheck() {
        countdownHandler.removeCallbacks(countdownCheck);
        long delay = COUNTDOWN_CHECK_LIMIT_MILLIS;
        try {
            LocalDateTime now = toLocalTime(System.currentTimeMillis());
            LocalDateTime next = CountedEvents.nextEnd(store.load(), now);
            if (next != null) {
                long untilEnd = Duration.between(now, next).toMillis() + COUNTDOWN_CHECK_SLACK_MILLIS;
                delay = Math.max(COUNTDOWN_CHECK_SLACK_MILLIS, Math.min(delay, untilEnd));
            }
        } catch (IOException e) {
            // Check again in a minute.
        }
        countdownHandler.postDelayed(countdownCheck, delay);
    }

    private void checkCountdown() {
        if (applyCountdown()) {
            refresh();
        }
        scheduleCountdownCheck();
    }

    private static LocalDateTime toLocalTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    private void setFilter(int color) {
        filterColor = color;
        dayAdapter.setFilter(color);
        if (isWide()) {
            weekView.setWeek(shownEvents, color);
        }
        showFilterState();
    }

    /**
     * Shows the filter on the Filter button: it is outlined in the filter's color and says the
     * color's name, or goes back to plain "Filter" with no filter.
     */
    private void showFilterState() {
        if (filterButton == null) {
            return;
        }
        boolean on = filterColor != ColorWheelDialog.CLEAR;
        filterButton.setStrokeColor(ColorStateList.valueOf(on ? filterColor : Color.TRANSPARENT));
        String label = on ? colorNames.nameOf(filterColor) : getString(R.string.filter);
        filterButton.setText(label);
        filterButton.setContentDescription(on
                ? getString(R.string.filter_on, label) : getString(R.string.filter));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        // Snap to Day moves the day pager, which wide mode doesn't show.
        menu.findItem(R.id.snap_to_day).setVisible(!isWide());
        View snapButton = menu.findItem(R.id.snap_to_day).getActionView();
        if (snapButton != null) {
            snapButton.setOnClickListener(view -> snapTaps.tap());
        }
        View settingsFrame = menu.findItem(R.id.settings).getActionView();
        if (settingsFrame != null) {
            settingsFrame.findViewById(R.id.settings_button).setOnClickListener(
                    view -> startActivity(new Intent(this, SettingsActivity.class)));
        }
        filterButton = (MaterialButton) menu.findItem(R.id.filter).getActionView();
        if (filterButton != null) {
            filterButton.setOnClickListener(view -> filterTaps.tap());
            // Once laid out with "Filter", remember the width: names can grow the button to the
            // left up to FILTER_MAX_WIDTH_FACTOR times that, then end in "...", and it never
            // shrinks below it.
            filterButton.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                    oldLeft, oldTop, oldRight, oldBottom) -> {
                if (filterButtonBaseWidth == 0 && right > left) {
                    filterButtonBaseWidth = right - left;
                    filterButton.setMinWidth(filterButtonBaseWidth);
                    filterButton.setMaxWidth((int) (FILTER_MAX_WIDTH_FACTOR * filterButtonBaseWidth));
                }
            });
            showFilterState();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.filter) {
            filterTaps.tap();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showFilterWheel() {
        boolean filtering = filterColor != ColorWheelDialog.CLEAR;
        ColorWheelDialog.show(this, R.string.filter_title, filterColor, this::setFilter,
                filtering ? R.string.clear_filter : R.string.cancel,
                filtering ? () -> setFilter(ColorWheelDialog.CLEAR) : null,
                this::showFilterState);
    }

    private void snapToToday() {
        dayPager.setCurrentItem(WeekPages.nearestPage(LocalDate.now().getDayOfWeek(),
                dayPager.getCurrentItem()), true);
    }

    private void showSnapToDay() {
        DayOfWeek[] days = WeekDays.SUNDAY_FIRST;
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        DayOfWeek showing = WeekPages.dayAt(dayPager.getCurrentItem());
        String[] names = new String[days.length];
        int current = 0;
        for (int i = 0; i < days.length; i++) {
            String name = days[i].getDisplayName(TextStyle.FULL, Locale.getDefault());
            names[i] = days[i] == today ? getString(R.string.today_title, name) : name;
            if (days[i] == showing) {
                current = i;
            }
        }
        new FeedbackDialogBuilder(this, R.style.ThemeOverlay_TSched_Dialog_SmallTitle)
                .setTitle(R.string.snap_to_day)
                .setSingleChoiceItems(new ArrayAdapter<>(this, R.layout.item_snap_day, names), current, (dialog, which) -> {
                    dayPager.setCurrentItem(
                            WeekPages.nearestPage(days[which], dayPager.getCurrentItem()), true);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onBlockTapped(Event event) {
        EventDialogs.showDetails(this, event, this::refresh);
    }

    @Override
    public void onBlockHeld(Event event) {
        EventDialogs.showDetails(this, event, this::refresh);
    }
}
