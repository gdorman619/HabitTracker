package com.hermes.habittracker;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.Notification;

/**
 * Fires when a habit's daily reminder alarm goes off. Builds a notification with
 * Done / Open / Later actions. If the habit is already done today, the reminder is
 * suppressed (no nagging). After showing, it reschedules itself for the next day.
 */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ReminderScheduler.ACTION_REMINDER.equals(intent.getAction())) return;
        int habitId = intent.getIntExtra(ReminderScheduler.EXTRA_HABIT_ID, -1);
        if (habitId == -1) return;

        HabitStorage storage = new HabitStorage(ctx);
        Habit h = null;
        for (Habit x : storage.loadHabits()) if (x.id == habitId) { h = x; break; }
        if (h == null) { ReminderScheduler.cancel(ctx, new Habit(habitId, "", "")); return; }

        // Skip if already done today - no point nagging a completed habit.
        if (h.isDoneToday()) {
            // Still reschedule so tomorrow's reminder fires.
            ReminderScheduler.schedule(ctx, h);
            return;
        }

        ReminderScheduler.ensureChannel(ctx);

        // Done action
        Intent done = new Intent(ctx, ReminderActionReceiver.class);
        done.setAction(ReminderScheduler.ACTION_DONE);
        done.putExtra(ReminderScheduler.EXTRA_HABIT_ID, habitId);
        PendingIntent piDone = PendingIntent.getBroadcast(ctx, habitId * 10 + 1, done,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Open action
        Intent open = new Intent(ctx, MainActivity.class);
        open.setAction(ReminderScheduler.ACTION_OPEN);
        open.putExtra(ReminderScheduler.EXTRA_HABIT_ID, habitId);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(ctx, habitId * 10 + 2, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Later (snooze 1h) action
        Intent later = new Intent(ctx, ReminderActionReceiver.class);
        later.setAction(ReminderScheduler.ACTION_LATER);
        later.putExtra(ReminderScheduler.EXTRA_HABIT_ID, habitId);
        PendingIntent piLater = PendingIntent.getBroadcast(ctx, habitId * 10 + 3, later,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = h.emoji + " " + h.name;
        String body = "Time for today's " + h.name + ". Tap Done to keep your streak alive.";

        Notification n = new Notification.Builder(ctx, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email) // placeholder; real launcher icon set below
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(piOpen)
                .setAutoCancel(true)
                .addAction(android.R.drawable.checkbox_on_background, "Done", piDone)
                .addAction(android.R.drawable.ic_popup_sync, "Later", piLater)
                .addAction(android.R.drawable.ic_menu_view, "Open", piOpen)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .build();
        try {
            n.icon = ctx.getApplicationInfo().icon; // use the app's launcher icon
        } catch (Exception ignored) {}

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(habitId, n);

        // Reschedule for tomorrow (the alarm only fires once).
        ReminderScheduler.schedule(ctx, h);
    }
}
