package com.hermes.habittracker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.List;

public class StreaksWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TOGGLE = "com.hermes.habittracker.TOGGLE_HABIT";
    public static final String EXTRA_HABIT_ID = "habit_id";
    public static final String EXTRA_WIDGET_ID = "widget_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            int habitId = intent.getIntExtra(EXTRA_HABIT_ID, -1);
            int widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (habitId != -1) {
                HabitStorage storage = new HabitStorage(context);
                List<Habit> habits = storage.loadHabits();
                for (Habit h : habits) {
                    if (h.id == habitId) {
                        h.toggleToday();
                        storage.updateHabit(h);
                        break;
                    }
                }
                AppWidgetManager manager = AppWidgetManager.getInstance(context);
                onUpdate(context, manager, new int[]{widgetId});
            }
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        HabitStorage storage = new HabitStorage(context);
        List<Habit> habits = storage.loadHabits();

        // Sort: not-done-first (by creation order), then done (by creation order)
        List<Habit> notDone = new ArrayList<>();
        List<Habit> done = new ArrayList<>();
        for (Habit h : habits) {
            if (h.isDoneToday()) done.add(h);
            else notDone.add(h);
        }
        List<Habit> sorted = new ArrayList<>();
        sorted.addAll(notDone);
        sorted.addAll(done);

        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_streaks);

            int doneCount = done.size();
            views.setTextViewText(R.id.widgetCount, doneCount + "/" + habits.size() + " done");

            // Clear old rows
            views.removeAllViews(R.id.widgetRows);

            if (habits.isEmpty()) {
                views.setViewVisibility(R.id.widgetRows, View.GONE);
                views.setViewVisibility(R.id.widgetEmpty, View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.widgetRows, View.VISIBLE);
                views.setViewVisibility(R.id.widgetEmpty, View.GONE);

                int maxRows = Math.min(sorted.size(), 4);
                for (int i = 0; i < maxRows; i++) {
                    Habit h = sorted.get(i);
                    RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_row);
                    row.setTextViewText(R.id.wRowEmoji, h.emoji);
                    row.setTextViewText(R.id.wRowName, h.name);
                    int streak = h.getStreak();
                    row.setTextViewText(R.id.wRowStreak, streak > 0 ? "\uD83D\uDD25" + streak : "");
                    row.setTextViewText(R.id.wRowCheck, h.isDoneToday() ? "\u2705" : "\u2B1C");

                    // Dim completed habits slightly for visual separation
                    if (h.isDoneToday()) {
                        row.setInt(R.id.wRowName, "setTextColor", 0x889aa7b4);
                        row.setInt(R.id.wRowEmoji, "setAlpha", 140);
                    }

                    // Click on the row toggles the habit
                    Intent toggleIntent = new Intent(context, StreaksWidgetProvider.class);
                    toggleIntent.setAction(ACTION_TOGGLE);
                    toggleIntent.putExtra(EXTRA_HABIT_ID, h.id);
                    toggleIntent.putExtra(EXTRA_WIDGET_ID, id);
                    PendingIntent pi = PendingIntent.getBroadcast(context, h.id, toggleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    row.setOnClickPendingIntent(R.id.wRowEmoji, pi);
                    row.setOnClickPendingIntent(R.id.wRowName, pi);
                    row.setOnClickPendingIntent(R.id.wRowStreak, pi);
                    row.setOnClickPendingIntent(R.id.wRowCheck, pi);

                    views.addView(R.id.widgetRows, row);
                }

                if (sorted.size() > 4) {
                    RemoteViews more = new RemoteViews(context.getPackageName(), R.layout.widget_row);
                    more.setTextViewText(R.id.wRowEmoji, "\u2022");
                    more.setTextViewText(R.id.wRowName, "+" + (sorted.size() - 4) + " more");
                    more.setTextViewText(R.id.wRowCheck, "");
                    views.addView(R.id.widgetRows, more);
                }
            }

            // Tap header/empty to open app
            views.setOnClickPendingIntent(R.id.widgetCount, pi_open(context, id));
            views.setOnClickPendingIntent(R.id.widgetEmpty, pi_open(context, id));

            manager.updateAppWidget(id, views);
        }
    }

    private static PendingIntent pi_open(Context context, int id) {
        Intent openIntent = new Intent(context, MainActivity.class);
        return PendingIntent.getActivity(context, 1000 + id, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, StreaksWidgetProvider.class));
        if (ids.length > 0) {
            new StreaksWidgetProvider().onUpdate(context, manager, ids);
        }
    }
}
