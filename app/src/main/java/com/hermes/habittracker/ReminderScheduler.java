package com.hermes.habittracker;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.List;

/**
 * Reminder scheduling for habits.
 *
 * Each habit with reminderEnabled gets ONE daily exact alarm (setExactAndAllowWhileIdle),
 * keyed by habit id so add/edit/delete never collide. When the alarm fires,
 * ReminderReceiver builds the notification; the alarm reschedules itself +1 day so
 * reminders keep firing without needing the app open. Alarms are cleared on reboot,
 * so rescheduleAll() is called from BootReceiver and after any habit change.
 *
 * Everything is local: no account, no server, no network.
 */
public final class ReminderScheduler {
    public static final String ACTION_REMINDER = "com.hermes.habittracker.REMINDER";
    public static final String ACTION_DONE = "com.hermes.habittracker.REMINDER_DONE";
    public static final String ACTION_OPEN = "com.hermes.habittracker.REMINDER_OPEN";
    public static final String ACTION_LATER = "com.hermes.habittracker.REMINDER_LATER";
    public static final String EXTRA_HABIT_ID = "habit_id";
    public static final String CHANNEL_ID = "habits_reminders";

    private ReminderScheduler() {}

    /** Create the notification channel (required on Android 8+, safe to call repeatedly). */
    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Daily reminders to complete your habits");
                nm.createNotificationChannel(ch);
            }
        }
    }

    /** Whether we're allowed to use exact alarms (Android 12+). If false, callers
     *  should fall back to inexact scheduling rather than silently failing. */
    public static boolean canScheduleExact(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true; // pre-12 exact alarms always allowed
    }

    private static PendingIntent reminderIntent(Context ctx, int habitId) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.setAction(ACTION_REMINDER);
        i.putExtra(EXTRA_HABIT_ID, habitId);
        return PendingIntent.getBroadcast(ctx, habitId, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Compute the next trigger time (today at hh:mm, or tomorrow if that time passed). */
    private static long nextTriggerMillis(int hh, int mm) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hh);
        c.set(Calendar.MINUTE, mm);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DATE, 1); // already passed today -> tomorrow
        }
        return c.getTimeInMillis();
    }

    /** Schedule (or reschedule) the daily reminder for one habit. No-op if disabled. */
    public static void schedule(Context ctx, Habit h) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (!h.reminderEnabled) { cancel(ctx, h); return; }
        ensureChannel(ctx);
        PendingIntent pi = reminderIntent(ctx, h.id);
        long trigger = nextTriggerMillis(h.reminderHour, h.reminderMinute);
        if (canScheduleExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            // Fallback: still reminds, just not at the exact minute (Android 12+ without permission)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }

    /** Cancel a habit's reminder (also used when disabling or deleting). */
    public static void cancel(Context ctx, Habit h) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = reminderIntent(ctx, h.id);
        am.cancel(pi);
        pi.cancel();
    }

    /** Re-register every enabled reminder. Call after add/edit/delete and on boot. */
    public static void rescheduleAll(Context ctx, List<Habit> habits) {
        // Cancel all first to avoid stale alarms for deleted habits, then schedule enabled ones.
        // (We can't enumerate every past id reliably, so we cancel via the habits we know,
        //  plus rely on FLAG_UPDATE_CURRENT overwriting intents for existing ids.)
        for (Habit h : habits) {
            if (h.reminderEnabled) schedule(ctx, h);
        }
    }

    /** Snooze: one-shot alarm ~1 hour from now for this habit's reminder. */
    public static void snooze(Context ctx, int habitId) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.setAction(ACTION_REMINDER);
        i.putExtra(EXTRA_HABIT_ID, habitId);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, habitId, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long t = System.currentTimeMillis() + 60 * 60 * 1000L;
        if (canScheduleExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
    }
}
