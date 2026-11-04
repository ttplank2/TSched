package edu.illinois.ttplank2.tsched;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Supplies one page per day for the swipeable calendar. */
public class DayPagerAdapter extends RecyclerView.Adapter<DayPagerAdapter.DayPage> {
    /** How far before the current time today's grid starts, when set to start at the current time. */
    private static final int TODAY_LEAD_MINUTES = 3 * 60;

    private final DayScheduleView.BlockListener blockListener;
    private List<Event> events = new ArrayList<>();
    private int filterColor = ColorWheelDialog.CLEAR;
    /** Where each day's grid starts scrolled to, whatever is scheduled earlier (scroll up to see it). */
    private int startMinute = StartTime.DEFAULT_MINUTE;
    /** Whether today's grid starts three hours before the current time instead. */
    private boolean todayStartsAtNow;

    public DayPagerAdapter(DayScheduleView.BlockListener blockListener) {
        this.blockListener = blockListener;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
        notifyItemRangeChanged(0, getItemCount());
    }

    /** Sets the time of day each day's grid starts scrolled to; days already shown move to it. */
    public void setStartMinute(int minute) {
        if (minute != startMinute) {
            startMinute = minute;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    /** Sets whether today's grid starts three hours before now rather than at the start time. */
    public void setTodayStartsAtNow(boolean startsAtNow) {
        if (startsAtNow != todayStartsAtNow) {
            todayStartsAtNow = startsAtNow;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    /** Grays every event not of {@code color}; {@link ColorWheelDialog#CLEAR} shows all colors. */
    public void setFilter(int color) {
        filterColor = color;
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public int getItemCount() {
        return WeekPages.PAGE_COUNT;
    }

    @NonNull
    @Override
    public DayPage onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_day_page, parent, false);
        return new DayPage(view, blockListener);
    }

    @Override
    public void onBindViewHolder(@NonNull DayPage page, int position) {
        DayOfWeek day = WeekPages.dayAt(position);
        page.bind(day, DaySegment.forDay(events, day), filterColor, startMinute, todayStartsAtNow);
    }

    static final class DayPage extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView subtitle;
        private final NestedScrollView scroller;
        private final DayScheduleView schedule;
        private DayOfWeek shownDay;
        private int shownStartMinute = Event.NO_TIME;
        private boolean shownAtNow;

        DayPage(View view, DayScheduleView.BlockListener blockListener) {
            super(view);
            title = view.findViewById(R.id.day_title);
            subtitle = view.findViewById(R.id.day_subtitle);
            scroller = view.findViewById(R.id.day_scroller);
            schedule = view.findViewById(R.id.day_schedule);
            schedule.setBlockListener(blockListener);
        }

        void bind(DayOfWeek day, List<DaySegment> daySegments, int filterColor, int startMinute,
                  boolean todayStartsAtNow) {
            boolean isToday = day == LocalDate.now().getDayOfWeek();
            String name = day.getDisplayName(TextStyle.FULL, Locale.getDefault());
            title.setText(isToday ? itemView.getContext().getString(R.string.today_title, name) : name);
            subtitle.setText(daySegments.isEmpty()
                    ? itemView.getContext().getString(R.string.nothing_scheduled)
                    : itemView.getResources().getQuantityString(
                            R.plurals.event_count, daySegments.size(), daySegments.size()));

            LocalTime now = LocalTime.now();
            schedule.setDay(daySegments, isToday ? now.getHour() * 60 + now.getMinute() : Event.NO_TIME,
                    filterColor);

            boolean startsAtNow = isToday && todayStartsAtNow;
            if (day != shownDay || startMinute != shownStartMinute || startsAtNow != shownAtNow) {
                shownDay = day;
                shownStartMinute = startMinute;
                shownAtNow = startsAtNow;
                int scrollMinute = startsAtNow
                        ? Math.max(0, now.getHour() * 60 + now.getMinute() - TODAY_LEAD_MINUTES)
                        : StartTime.scrollMinute(startMinute);
                // The grid has a little space above midnight; a start at midnight shows all of it.
                int target = scrollMinute == 0 ? 0 : schedule.offsetOf(scrollMinute);
                scroller.post(() -> scroller.scrollTo(0, target));
            }
        }
    }
}
