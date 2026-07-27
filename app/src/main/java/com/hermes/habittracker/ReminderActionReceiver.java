package com.hermes.habittracker;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Handles the user's tap on a reminder notification action:
 *   - Done: mark the habit complete today, refresh the widget, dismiss the notification.
 *   - Later: snooze ~1 hour, dismiss the notification.
 *   - Open: launch the app (handled by MainActivity via the activity PendingIntent;
 *           this receiver only covers Done/Later).
 */
public class ReminderActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        int habitId = intent.getIntExtra(ReminderScheduler.EXTRA_HABIT_ID, -1);
        if (habitId == -1) return;

        HabitStorage storage = new HabitStorage(ctx);

        if (ReminderScheduler.ACTION_DONE.equals(action)) {
            for (Habit h : storage.loadHabits()) {
                if (h.id == habitId) {
                    h.markDoneToday();
                    storage.updateHabit(h);
                    StreaksWidgetProvider.updateAllWidgets(ctx);
                    break;
                }
            }
            dismiss(ctx, habitId);
        } else if (ReminderScheduler.ACTION_LATER.equals(action)) {
            ReminderScheduler.snooze(ctx, habitId);
            dismiss(ctx, habitId);
        }
    }

    private void dismiss(Context ctx, int habitId) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(habitId);
    }
}
