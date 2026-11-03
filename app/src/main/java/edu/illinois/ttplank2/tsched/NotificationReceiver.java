package edu.illinois.ttplank2.tsched;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs when the alarm for an event notification goes off: shows a notification for each event due at
 * that moment, then sets the alarm for the next one.
 */
public final class NotificationReceiver extends BroadcastReceiver {
    /** How far from the alarm's moment an event's notification may be and still count as due. */
    private static final long SLACK_MILLIS = 60_000;

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            long firedFor = intent.getLongExtra(NotificationScheduler.EXTRA_NOTIFY_AT, -1);
            if (firedFor >= 0) {
                showDue(context, firedFor);
            }
        } finally {
            NotificationScheduler.reschedule(context);
        }
    }

    private void showDue(Context context, long firedForMillis) {
        NotificationSettings settings = new NotificationSettings(context);
        if (!settings.isOn() || !NotificationScheduler.mayShowNotifications(context)) {
            return;
        }
        List<Event> events;
        try {
            events = new EventStore(context).load();
        } catch (IOException e) {
            return;
        }
        // What was due at that moment, worked out again from what is saved now, so an event edited or
        // deleted since the alarm was set does not notify.
        LocalDateTime justBefore = NotificationScheduler.timeOf(firedForMillis).minusSeconds(1);
        NotificationPlanner.Next due = NotificationPlanner.next(events, settings.colors(),
                settings.leadMinutes(), justBefore);
        if (due == null) {
            return;
        }
        long dueMillis = due.notifyAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (Math.abs(dueMillis - firedForMillis) > SLACK_MILLIS) {
            return;
        }
        NotificationScheduler.createChannel(context);
        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        for (Event event : due.events) {
            String text = context.getString(R.string.notification_text, event.getName(),
                    TimeFormat.clock(event.getStartMinute()));
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                    NotificationScheduler.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(text)
                    .setColor(event.getColor())
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_EVENT);
            // Two events at once need two notifications, and the same event a week later replaces itself.
            try {
                manager.notify(event.getId().hashCode(), builder.build());
            } catch (SecurityException notAllowed) {
                // The permission was taken back since it was last checked: nothing to show.
                return;
            }
        }
    }
}
