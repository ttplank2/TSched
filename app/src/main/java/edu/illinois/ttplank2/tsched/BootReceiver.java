package edu.illinois.ttplank2.tsched;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Sets the next event notification again after the phone restarts, which clears every alarm. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            NotificationScheduler.reschedule(context);
        }
    }
}
