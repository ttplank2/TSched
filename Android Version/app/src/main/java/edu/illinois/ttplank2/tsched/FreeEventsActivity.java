package edu.illinois.ttplank2.tsched;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;

import java.io.IOException;
import java.util.List;

/**
 * A board of every free event that hasn't been placed on the calendar yet, with buttons to place
 * them all or to recall every placed one back here.
 */
public class FreeEventsActivity extends FeedbackActivity {
    private LinearLayout freeList;
    private TextView emptyMessage;
    private TextView errorMessage;
    private Button placeButton;
    private Button recallAllButton;
    private int placedCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_free_events);
        setTitle(R.string.unplaced_free_events);
        freeList = findViewById(R.id.free_list);
        emptyMessage = findViewById(R.id.empty_message);
        errorMessage = findViewById(R.id.error_message);
        placeButton = findViewById(R.id.place_events_button);
        placeButton.setOnClickListener(
                view -> startActivity(new Intent(this, PlaceEventsActivity.class)));
        recallAllButton = findViewById(R.id.recall_all_button);
        recallAllButton.setOnClickListener(view -> confirmRecallAll());
    }

    @Override
    protected void onResume() {
        super.onResume();
        showFreeEvents();
    }

    private void showFreeEvents() {
        freeList.removeAllViews();
        placedCount = 0;
        try {
            List<Event> events = new EventStore(this).load();
            placedCount = FreeEventPlacer.countPlaced(events);
            for (EventRows.Row row : EventRows.unplacedFree(events)) {
                addCard(row);
            }
            errorMessage.setVisibility(View.GONE);
        } catch (IOException e) {
            errorMessage.setVisibility(View.VISIBLE);
        }
        boolean empty = freeList.getChildCount() == 0;
        emptyMessage.setVisibility(empty ? View.VISIBLE : View.GONE);
        placeButton.setEnabled(!empty);
        recallAllButton.setEnabled(placedCount > 0);
    }

    /** Asks before taking every placed free event off the calendar. */
    private void confirmRecallAll() {
        new FeedbackDialogBuilder(this)
                .setTitle(getResources().getQuantityString(R.plurals.recall_all_title, placedCount, placedCount))
                .setMessage(getResources().getQuantityString(R.plurals.recall_all_message, placedCount))
                .setPositiveButton(R.string.recall_all_confirm, (dialog, which) -> recallAll())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void recallAll() {
        EventStore store = new EventStore(this);
        try {
            List<Event> events = store.load();
            int recalled = FreeEventPlacer.countPlaced(events);
            store.save(FreeEventPlacer.recallAll(events));
            Toast.makeText(this, getResources().getQuantityString(R.plurals.recalled_count, recalled, recalled),
                    Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
        }
        showFreeEvents();
    }

    private void addCard(EventRows.Row row) {
        Event event = row.first;
        View card = getLayoutInflater().inflate(R.layout.item_free_event, freeList, false);
        ((TextView) card.findViewById(R.id.free_name)).setText(event.getName());
        ((MaterialCardView) card).setStrokeColor(event.getColor());
        ((TextView) card.findViewById(R.id.free_details)).setText(EventDialogs.describeFreeGroup(this, row.members));
        card.setOnClickListener(view -> EventDialogs.showDetails(this, event, this::showFreeEvents));
        card.setOnLongClickListener(view -> {
            EventDialogs.showDetails(this, event, this::showFreeEvents);
            return true;
        });
        freeList.addView(card);
    }
}
