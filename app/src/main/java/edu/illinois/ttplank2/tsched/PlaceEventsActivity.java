package edu.illinois.ttplank2.tsched;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.function.IntConsumer;

/**
 * Asks which hours of the day free events may go in, then suggests a placement for every
 * unplaced free event. Nothing is saved until the user approves the suggestion.
 */
public class PlaceEventsActivity extends FeedbackActivity {
    private static final int DEFAULT_START = 8 * 60;
    private static final int DEFAULT_END = 22 * 60;

    private final Random random = new Random();
    private Button fromButton;
    private Button toButton;
    private TextView errorText;
    private int windowStart = DEFAULT_START;
    private int windowEnd = DEFAULT_END;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_events);
        setTitle(R.string.place_events);

        fromButton = findViewById(R.id.window_from_button);
        toButton = findViewById(R.id.window_to_button);
        errorText = findViewById(R.id.error_text);
        fromButton.setText(TimeFormat.clock(windowStart));
        toButton.setText(TimeFormat.clock(windowEnd));
        fromButton.setOnClickListener(view -> pickTime(fromButton, windowStart, minute -> windowStart = minute));
        toButton.setOnClickListener(view -> pickTime(toButton, windowEnd, minute -> windowEnd = minute));
        findViewById(R.id.place_button).setOnClickListener(view -> suggest());
    }

    private void pickTime(Button button, int currentMinute, IntConsumer onPicked) {
        TimePickerDialog dialog = new TimePickerDialog(AccentPalette.popupContext(this), (picker, hour, minute) -> {
            int minuteOfDay = hour * 60 + minute;
            onPicked.accept(minuteOfDay);
            button.setText(TimeFormat.clock(minuteOfDay));
            errorText.setVisibility(View.GONE);
        }, currentMinute / 60, currentMinute % 60, false);
        Feedback.watchTaps(dialog.getWindow());
        AccentPalette.watchDialog(dialog);
        dialog.show();
        Feedback.tickOnTimeChanges(dialog);
    }

    private void suggest() {
        List<Event> week;
        FreeEventPlacer.Suggestion suggestion;
        try {
            week = new EventStore(this).load();
            suggestion = FreeEventPlacer.suggest(week, windowStart, windowEnd, random);
        } catch (IOException e) {
            showError(getString(R.string.load_failed));
            return;
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }
        if (suggestion.placed.isEmpty() && suggestion.unfitted.isEmpty()) {
            showError(getString(R.string.nothing_to_place));
            return;
        }
        errorText.setVisibility(View.GONE);

        View content = getLayoutInflater().inflate(R.layout.dialog_placement, null);
        ((TextView) content.findViewById(R.id.placement_text)).setText(describe(suggestion));
        AlertDialog dialog = new FeedbackDialogBuilder(this).setView(content).create();
        content.findViewById(R.id.placement_cancel).setOnClickListener(view -> dialog.dismiss());
        View another = content.findViewById(R.id.placement_another);
        View approve = content.findViewById(R.id.placement_approve);
        if (suggestion.placed.isEmpty()) {
            another.setVisibility(View.GONE);
            approve.setVisibility(View.GONE);
        }
        another.setOnClickListener(view -> {
            dialog.dismiss();
            suggest();
        });
        approve.setOnClickListener(view -> {
            dialog.dismiss();
            approve(suggestion);
        });
        dialog.show();
    }

    private String describe(FreeEventPlacer.Suggestion suggestion) {
        StringBuilder text = new StringBuilder();
        for (List<Event> block : PlacementBlocks.of(suggestion.placed)) {
            // The name and a colon, then a line for each day: "Wed, 2:00 – 4:00 PM". A blank line
            // follows every event so they are easy to tell apart.
            text.append(getString(R.string.placed_group_header, block.get(0).getName())).append('\n');
            for (Event event : block) {
                text.append(getString(R.string.placed_group_line,
                        WeekDays.shortName(this, event.getDay()),
                        TimeFormat.range(event.getStartMinute(), event.getEndMinute()))).append('\n');
            }
            text.append('\n');
        }
        if (!suggestion.unfitted.isEmpty()) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(getString(R.string.did_not_fit_header)).append('\n');
            for (Event event : suggestion.unfitted) {
                text.append(getString(R.string.unfitted_line, event.getName(),
                        TimeFormat.length(event.getDurationMinutes()))).append('\n');
            }
        }
        return text.toString().trim();
    }

    private void approve(FreeEventPlacer.Suggestion suggestion) {
        try {
            EventStore store = new EventStore(this);
            for (Event event : suggestion.placed) {
                store.put(event);
            }
        } catch (IOException e) {
            showError(getString(R.string.save_failed));
            return;
        }
        Toast.makeText(this, getResources().getQuantityString(R.plurals.placed_count,
                suggestion.placed.size(), suggestion.placed.size()), Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}
