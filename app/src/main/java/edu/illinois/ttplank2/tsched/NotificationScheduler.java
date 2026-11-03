package edu.illinois.ttplank2.tsched;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Keeps one alarm set for the next event notification. When it goes off, {@link NotificationReceiver}
 * shows the notification and sets the next one. The alarm is set again whenever something that could
 * change it does: events saved, Settings changed, the phone restarted, or the calendar opened.
 */
final class NotificationScheduler {
    static final String CHANNEL_ID = "event_reminders";
    static final String EXTRA_NOTIFY_AT = "notify_at_millis";

    private NotificationScheduler() {
    }

    /** Makes the one alarm match the events and settings as they are now (or none, if nothing will notify). */
    static void reschedule(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        alarms.cancel(alarmIntent(app, 0));
        NotificationSettings settings = new NotificationSettings(app);
        if (!settings.isOn() || !mayShowNotifications(app)) {
            return;
        }
        List<Event> events;
        try {
            events = new EventStore(app).load();
        } catch (IOException e) {
            return;
        }
        NotificationPlanner.Next next = NotificationPlanner.next(events, settings.colors(),
                settings.leadMinutes(), LocalDateTime.now());
        if (next == null) {
            return;
        }
        long at = next.notifyAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        PendingIntent intent = alarmIntent(app, at);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            // Without the exact-alarm permission Android may be a few minutes late, but still comes.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent);
        }
    }

    /** Whether Android will let the app show notifications (from Android 13 the user must allow it). */
    static boolean mayShowNotifications(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** The notification channel the reminders use, which Android needs before it will show them. */
    static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    /** The alarm's intent. Every one is equal as far as Android is concerned, so setting one replaces the last. */
    private static PendingIntent alarmIntent(Context context, long notifyAtMillis) {
        Intent intent = new Intent(context, NotificationReceiver.class)
                .putExtra(EXTRA_NOTIFY_AT, notifyAtMillis);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static LocalDateTime timeOf(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
}
