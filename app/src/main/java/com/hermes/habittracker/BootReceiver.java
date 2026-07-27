package com.hermes.habittracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Re-registers all habit reminders after the device reboots. Android clears all
 * AlarmManager alarms on boot, so without this reminders would silently stop
 * firing until the app was next opened.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        HabitStorage storage = new HabitStorage(ctx);
        ReminderScheduler.rescheduleAll(ctx, storage.loadHabits());
    }
}
