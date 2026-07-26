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

            Intent openIntent = new Intent(context, MainActivity.class);
            PendingIntent openPi = PendingIntent.getActivity(context, 1000 + id, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widgetCount, openPi);
            views.setOnClickPendingIntent(R.id.widgetEmpty, openPi);

            manager.updateAppWidget(id, views);
        }
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, StreaksWidgetProvider.class));
        if (ids.length > 0) {
            new StreaksWidgetProvider().onUpdate(context, manager, ids);
        }
    }
}
