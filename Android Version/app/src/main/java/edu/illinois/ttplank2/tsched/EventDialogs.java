package edu.illinois.ttplank2.tsched;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The details, delete, and recall dialogs for an event, shared by every screen that lists
 * events. Tapping or holding an event opens the same details popup, with Edit and Delete.
 */
public final class EventDialogs {
    private EventDialogs() {
    }

    /**
     * Shows the event's details, with Edit and Delete buttons. A placed free event also gets a
     * yellow Recall button underneath, which sends it back to Unplaced Free Events.
     *
     * @param onChanged runs after the event is deleted or recalled, to refresh the screen
     */
    public static void showDetails(Context context, Event event, Runnable onChanged) {
        AlertDialog dialog = new FeedbackDialogBuilder(context)
                .setTitle(event.getName())
                .setMessage(detailsOf(context, event))
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.edit, (d, which) -> openEditor(context, event))
                .setNeutralButton(R.string.delete, (d, which) -> confirmDelete(context, event, onChanged))
                .show();
        if (event.isPlaced()) {
            addRecallButton(context, dialog, event, onChanged);
        }
    }

    /** Adds the Recall tab below the dialog's buttons (the dialog's own content area sits above them). */
    private static void addRecallButton(Context context, AlertDialog dialog, Event event, Runnable onChanged) {
        ViewGroup panel = dialog.findViewById(androidx.appcompat.R.id.parentPanel);
        if (panel == null) {
            return;
        }
        View recall = LayoutInflater.from(dialog.getContext()).inflate(R.layout.dialog_recall_button, panel, false);
        recall.setOnClickListener(view -> {
            dialog.dismiss();
            confirmRecall(context, event, onChanged);
        });
        panel.addView(recall);
    }

    /** Asks before recalling; on yes, unplaces the event and runs {@code onRecalled}. */
    public static void confirmRecall(Context context, Event event, Runnable onRecalled) {
        new FeedbackDialogBuilder(context)
                .setTitle(context.getString(R.string.recall_title, event.getName()))
                .setMessage(event.isGrouped() ? R.string.recall_group_message : R.string.recall_message)
                .setPositiveButton(R.string.recall, (dialog, which) -> {
                    try {
                        EventStore store = new EventStore(context);
                        store.save(EventGroups.recalled(store.load(), Set.of(event.getId())));
                        Toast.makeText(context, context.getString(R.string.recalled, event.getName()),
                                Toast.LENGTH_SHORT).show();
                        onRecalled.run();
                    } catch (IOException e) {
                        Toast.makeText(context, R.string.save_failed, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void openEditor(Context context, Event event) {
        context.startActivity(new Intent(context, AddEventActivity.class)
                .putExtra(AddEventActivity.EXTRA_EVENT_ID, event.getId()));
    }

    /** Asks before deleting; on yes, removes the event from storage and runs {@code onDeleted}. */
    public static void confirmDelete(Context context, Event event, Runnable onDeleted) {
        MaterialAlertDialogBuilder builder = new FeedbackDialogBuilder(context)
                .setTitle(context.getString(R.string.delete_title, event.getName()));
        if (event.isGrouped()) {
            builder.setMessage(R.string.delete_group_message);
        }
        builder.setPositiveButton(R.string.delete, (dialog, which) -> {
                    try {
                        new EventStore(context).delete(event);
                        Toast.makeText(context, R.string.deleted, Toast.LENGTH_SHORT).show();
                        onDeleted.run();
                    } catch (IOException e) {
                        Toast.makeText(context, R.string.save_failed, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** One or two lines saying when the event happens (and where, if known). */
    public static String describe(Context context, Event event) {
        StringBuilder text = new StringBuilder();
        if (event.getKind() == Event.Kind.COUNTED) {
            text.append(context.getString(
                    event.crossesMidnight() ? R.string.detail_counted_overnight : R.string.detail_counted,
                    dayName(event.getDay()),
                    TimeFormat.range(event.getStartMinute(), event.getEndMinute()),
                    context.getResources().getQuantityString(
                            R.plurals.counts_left, event.getCount(), event.getCount())));
        } else if (event.getKind() == Event.Kind.FIXED) {
            text.append(context.getString(
                    event.crossesMidnight() ? R.string.detail_fixed_overnight : R.string.detail_fixed,
                    dayName(event.getDay()),
                    TimeFormat.range(event.getStartMinute(), event.getEndMinute())));
        } else if (event.isPlaced()) {
            text.append(context.getString(R.string.detail_placed, dayName(event.getDay()),
                    TimeFormat.range(event.getStartMinute(), event.getEndMinute())));
        } else if (event.getAllowedDays().size() == 1) {
            text.append(context.getString(R.string.free_one_day,
                    TimeFormat.length(event.getDurationMinutes()),
                    dayName(event.getAllowedDays().iterator().next())));
        } else if (event.hasDayLimit()) {
            text.append(context.getString(R.string.free_with_days,
                    TimeFormat.length(event.getDurationMinutes()),
                    allowedDaysList(context, event)));
        } else {
            text.append(context.getString(R.string.free_every_day,
                    TimeFormat.length(event.getDurationMinutes())));
        }
        if (!event.getLocation().isEmpty()) {
            text.append('\n').append(context.getString(R.string.detail_location, event.getLocation()));
        }
        return text.toString();
    }

    /**
     * The details of a group of free events, one for each day: the length and the days, such as
     * "Every Day", or a list of days. A lone event is described as usual.
     */
    public static String describeFreeGroup(Context context, List<Event> members) {
        Event first = members.get(0);
        if (members.size() == 1) {
            return describe(context, first);
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (Event member : members) {
            days.addAll(member.getAllowedDays());
        }
        String length = TimeFormat.length(first.getDurationMinutes());
        String text;
        if (days.size() == WeekDays.SUNDAY_FIRST.length) {
            text = context.getString(R.string.free_every_day, length);
        } else {
            StringBuilder names = new StringBuilder();
            for (DayOfWeek day : WeekDays.SUNDAY_FIRST) {
                if (days.contains(day)) {
                    names.append(names.length() == 0 ? "" : " · ")
                            .append(WeekDays.shortName(context, day));
                }
            }
            text = context.getString(R.string.free_with_days, length, names);
        }
        if (!first.getLocation().isEmpty()) {
            text += "\n" + context.getString(R.string.detail_location, first.getLocation());
        }
        return text;
    }

    /** The details of {@code event}, which for an unplaced free group lists all its days. */
    private static String detailsOf(Context context, Event event) {
        if (event.getKind() == Event.Kind.FREE && !event.isPlaced() && event.isGrouped()) {
            try {
                for (EventRows.Row row : EventRows.unplacedFree(new EventStore(context).load())) {
                    if (row.members.contains(event) || row.first.getId().equals(event.getId())) {
                        return describeFreeGroup(context, row.members);
                    }
                }
            } catch (IOException e) {
                // The plain description below still says what this one event is.
            }
        }
        return describe(context, event);
    }

    /** The event's allowed days, Sunday first, as "Sun · Mon · Tues". */
    private static String allowedDaysList(Context context, Event event) {
        StringBuilder names = new StringBuilder();
        for (DayOfWeek day : WeekDays.SUNDAY_FIRST) {
            if (event.allowsDay(day)) {
                names.append(names.length() == 0 ? "" : " · ")
                        .append(WeekDays.shortName(context, day));
            }
        }
        return names.toString();
    }

    static String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.getDefault());
    }
}
