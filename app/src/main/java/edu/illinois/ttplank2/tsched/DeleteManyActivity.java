package edu.illinois.ttplank2.tsched;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.IOException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A list of every event with a tick box on each, for deleting several at once. An event that
 * repeats on several days is one line, and deleting it deletes every one of its days.
 */
public class DeleteManyActivity extends FeedbackActivity {
    private LinearLayout eventList;
    private TextView emptyMessage;
    private TextView errorMessage;
    private Button deleteButton;
    private List<EventRows.Row> rows = new ArrayList<>();
    /** The ids of the rows (each row's first event) that are ticked. */
    private final Set<String> ticked = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_many);
        setTitle(R.string.delete_many_events);
        eventList = findViewById(R.id.event_list);
        emptyMessage = findViewById(R.id.empty_message);
        errorMessage = findViewById(R.id.error_message);
        deleteButton = findViewById(R.id.delete_selected_button);
        deleteButton.setOnClickListener(view -> confirmDelete());
    }

    @Override
    protected void onResume() {
        super.onResume();
        showRows();
    }

    private void showRows() {
        eventList.removeAllViews();
        try {
            rows = EventRows.build(new EventStore(this).load());
            errorMessage.setVisibility(View.GONE);
        } catch (IOException e) {
            rows = new ArrayList<>();
            errorMessage.setVisibility(View.VISIBLE);
        }
        Set<String> present = new HashSet<>();
        for (EventRows.Row row : rows) {
            present.add(row.first.getId());
            addCard(row);
        }
        ticked.retainAll(present);
        emptyMessage.setVisibility(rows.isEmpty() && errorMessage.getVisibility() == View.GONE
                ? View.VISIBLE : View.GONE);
        updateDeleteButton();
    }

    private void addCard(EventRows.Row row) {
        View card = getLayoutInflater().inflate(R.layout.item_delete_event, eventList, false);
        TextView name = card.findViewById(R.id.event_name);
        TextView details = card.findViewById(R.id.event_details);
        name.setText(row.first.getName());
        details.setText(describe(row));
        MaterialCardView cardView = (MaterialCardView) card;
        MaterialCheckBox check = card.findViewById(R.id.event_check);
        int color = row.first.getColor();
        if (row.first.getKind() == Event.Kind.FIXED) {
            // Like the calendar: a fixed event is a solid block of its color with dark text.
            int dark = ContextCompat.getColor(this, R.color.charcoal);
            cardView.setCardBackgroundColor(color);
            cardView.setStrokeWidth(0);
            name.setTextColor(dark);
            details.setTextColor(dark);
            check.setButtonTintList(ColorStateList.valueOf(dark));
            check.setButtonIconTintList(ColorStateList.valueOf(color));
        } else if (row.first.getKind() == Event.Kind.COUNTED) {
            // Like the calendar: a counted event is a dashed outline, which a card's own stroke
            // can't be, so the dashes are drawn as the background of what is inside the card.
            float density = getResources().getDisplayMetrics().density;
            GradientDrawable dashes = new GradientDrawable();
            dashes.setCornerRadius(12 * density);
            dashes.setStroke((int) (2 * density), color, 8 * density, 5 * density);
            cardView.setStrokeWidth(0);
            card.findViewById(R.id.event_content).setBackground(dashes);
        } else {
            // A free event is a solid outline in its color.
            cardView.setStrokeColor(color);
        }
        check.setChecked(ticked.contains(row.first.getId()));
        card.setOnClickListener(view -> {
            boolean nowTicked = !check.isChecked();
            check.setChecked(nowTicked);
            if (nowTicked) {
                ticked.add(row.first.getId());
            } else {
                ticked.remove(row.first.getId());
            }
            updateDeleteButton();
        });
        eventList.addView(card);
    }

    /** One or two lines saying when the event happens, listing every day if it repeats. */
    private String describe(EventRows.Row row) {
        if (row.members.size() == 1) {
            return EventDialogs.describe(this, row.first);
        }
        if (row.first.getKind() == Event.Kind.FREE) {
            return EventDialogs.describeFreeGroup(this, row.members);
        }
        StringBuilder days = new StringBuilder();
        for (DayOfWeek day : WeekDays.SUNDAY_FIRST) {
            for (Event member : row.members) {
                if (member.getDay() == day) {
                    days.append(days.length() == 0 ? "" : " · ").append(WeekDays.shortName(this, day));
                }
            }
        }
        String times = TimeFormat.range(row.first.getStartMinute(), row.first.getEndMinute());
        // One event on every day of the week reads "Every Day" rather than listing all seven.
        String text = row.members.size() == WeekDays.SUNDAY_FIRST.length
                ? getString(R.string.detail_every_day, times)
                : getString(R.string.detail_fixed_days, days.toString(), times);
        if (!row.first.getLocation().isEmpty()) {
            text += "\n" + getString(R.string.detail_location, row.first.getLocation());
        }
        return text;
    }

    private void updateDeleteButton() {
        deleteButton.setEnabled(!ticked.isEmpty());
        deleteButton.setText(ticked.isEmpty() ? getString(R.string.delete_selected)
                : getResources().getQuantityString(
                        R.plurals.delete_selected_count, ticked.size(), ticked.size()));
    }

    /** Asks before deleting; on yes, deletes every ticked event and shows the list again. */
    private void confirmDelete() {
        int count = ticked.size();
        new FeedbackDialogBuilder(this)
                .setTitle(getResources().getQuantityString(R.plurals.delete_many_title, count, count))
                .setMessage(R.string.delete_selected_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteTicked())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteTicked() {
        List<Event> doomed = new ArrayList<>();
        int count = 0;
        for (Iterator<EventRows.Row> it = rows.iterator(); it.hasNext(); ) {
            EventRows.Row row = it.next();
            if (ticked.contains(row.first.getId())) {
                doomed.addAll(row.members);
                count++;
            }
        }
        try {
            new EventStore(this).deleteAll(doomed);
        } catch (IOException e) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        ticked.clear();
        Toast.makeText(this, getResources().getQuantityString(R.plurals.deleted_many, count, count),
                Toast.LENGTH_SHORT).show();
        showRows();
    }
}
