package com.hermes.habittracker;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HabitDetailActivity extends AppCompatActivity {

    private HabitStorage storage;
    private Habit habit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int habitId = getIntent().getIntExtra("habit_id", -1);
        if (habitId == -1) { finish(); return; }

        storage = new HabitStorage(this);
        List<Habit> habits = storage.loadHabits();
        for (Habit h : habits) {
            if (h.id == habitId) { habit = h; break; }
        }
        if (habit == null) { finish(); return; }

        buildUI();
    }

    private void buildUI() {
        int pad = (int) (getResources().getDisplayMetrics().density * 20);
        int padSm = (int) (getResources().getDisplayMetrics().density * 12);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff0f1419);
        root.setPadding(pad, pad + (int)(getResources().getDisplayMetrics().density * 32), pad, pad);

        // Header: emoji + name
        TextView header = new TextView(this);
        header.setText(habit.emoji + "  " + habit.name);
        header.setTextSize(24);
        header.setTextColor(0xff4cc2ff);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, padSm);
        root.addView(header);

        // Streak info
        int streak = habit.getStreak();
        TextView streakText = new TextView(this);
        streakText.setText(streak > 0 ? "\uD83D\uDD25 " + streak + " day streak" : "No current streak");
        streakText.setTextSize(16);
        streakText.setTextColor(streak > 0 ? 0xffd29922 : 0xff9aa7b4);
        streakText.setPadding(0, 0, 0, pad);
        root.addView(streakText);

        // Freeze info
        TextView freezeText = new TextView(this);
        int remaining = storage.getFreezesRemaining();
        int max = storage.getMaxFreezes();
        freezeText.setText("\u2744\uFE0F Streak freezes: " + remaining + "/" + max + " remaining this month");
        freezeText.setTextSize(14);
        freezeText.setTextColor(0xff9aa7b4);
        freezeText.setPadding(0, 0, 0, pad);
        root.addView(freezeText);

        // History label
        TextView historyLabel = new TextView(this);
        historyLabel.setText("History (last 30 days)");
        historyLabel.setTextSize(18);
        historyLabel.setTextColor(0xffcdd9e5);
        historyLabel.setTypeface(historyLabel.getTypeface(), android.graphics.Typeface.BOLD);
        historyLabel.setPadding(0, 0, 0, padSm);
        root.addView(historyLabel);

        // Calendar grid: 5 rows x 7 cols (last 35 days)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        LinearLayout calendar = new LinearLayout(this);
        calendar.setOrientation(LinearLayout.VERTICAL);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -(35 - 1));
        // Align to start of week (Sunday)
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        // Day labels
        String[] dayLabels = {"S", "M", "T", "W", "T", "F", "S"};
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int d = 0; d < 7; d++) {
            TextView lbl = new TextView(this);
            lbl.setText(dayLabels[d]);
            lbl.setTextColor(0xff9aa7b4);
            lbl.setTextSize(11);
            lbl.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lbl.setLayoutParams(lp);
            labelRow.addView(lbl);
        }
        calendar.addView(labelRow);

        for (int row = 0; row < 5; row++) {
            LinearLayout weekRow = new LinearLayout(this);
            weekRow.setOrientation(LinearLayout.HORIZONTAL);
            weekRow.setPadding(0, padSm / 3, 0, padSm / 3);

            for (int col = 0; col < 7; col++) {
                String dateStr = sdf.format(cal.getTime());
                boolean completed = habit.completedDates.contains(dateStr);
                boolean isToday = dateStr.equals(sdf.format(new Date()));
                boolean isFuture = cal.getTimeInMillis() > System.currentTimeMillis() + 86400000;

                TextView day = new TextView(this);
                day.setText(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)));
                day.setTextSize(13);
                day.setGravity(Gravity.CENTER);

                if (isFuture) {
                    day.setTextColor(0x449aa7b4);
                } else if (completed) {
                    day.setTextColor(0xff0f1419);
                    day.setBackgroundColor(0xff3fb950);
                } else if (isToday) {
                    day.setTextColor(0xff4cc2ff);
                    day.setBackgroundColor(0x334cc2ff);
                } else {
                    day.setTextColor(0x889aa7b4);
                    day.setBackgroundColor(0x11ffffff);
                }

                int cellSize = (int) (getResources().getDisplayMetrics().density * 36);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cellSize, 1);
                lp.setMargins(2, 2, 2, 2);
                day.setLayoutParams(lp);
                weekRow.addView(day);
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            calendar.addView(weekRow);
        }
        root.addView(calendar);

        // Total completions
        TextView totalText = new TextView(this);
        totalText.setText("Total completions: " + habit.completedDates.size());
        totalText.setTextSize(14);
        totalText.setTextColor(0xff9aa7b4);
        totalText.setPadding(0, pad, 0, 0);
        root.addView(totalText);

        // Edit button
        TextView editBtn = new TextView(this);
        editBtn.setText("  Edit Habit  ");
        editBtn.setTextSize(15);
        editBtn.setTextColor(0xff0f1419);
        editBtn.setBackgroundColor(0xff4cc2ff);
        editBtn.setGravity(Gravity.CENTER);
        editBtn.setPadding(padSm, padSm, padSm, padSm);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        editLp.topMargin = pad;
        editBtn.setLayoutParams(editLp);
        editBtn.setOnClickListener(v -> showEditDialog());
        root.addView(editBtn);

        // Delete button
        TextView delBtn = new TextView(this);
        delBtn.setText("  Delete Habit  ");
        delBtn.setTextSize(15);
        delBtn.setTextColor(0xfff85149);
        delBtn.setGravity(Gravity.CENTER);
        delBtn.setPadding(padSm, padSm, padSm, padSm);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        delLp.topMargin = padSm;
        delBtn.setLayoutParams(delLp);
        delBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dark)
                .setTitle("Delete habit?")
                .setMessage(habit.name)
                .setPositiveButton("Delete", (d, w) -> {
                    storage.deleteHabit(habit.id);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        root.addView(delBtn);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void showEditDialog() {
        final String[] emojis = {
            "\uD83D\uDCAA", "\uD83C\uDFC8", "\uD83D\uDCDA", "\uD83C\uDF4E", "\u2705",
            "\uD83E\uDD8A", "\uD83D\uDE80", "\u263A\uFE0F", "\uD83C\uDFB5", "\uD83D\uDD25"
        };
        final int[] selectedIdx = {0};
        for (int i = 0; i < emojis.length; i++) {
            if (emojis[i].equals(habit.emoji)) { selectedIdx[0] = i; break; }
        }

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(60, 40, 60, 20);

        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(habit.name);
        input.setTextColor(0xffe6edf3);
        input.setTextSize(16);
        input.setHint("Habit name");
        input.setHintTextColor(0xff9aa7b4);
        dialogLayout.addView(input);

        TextView emojiLabel = new TextView(this);
        emojiLabel.setText("Pick an emoji:");
        emojiLabel.setTextColor(0xff9aa7b4);
        emojiLabel.setTextSize(14);
        LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lblLp.topMargin = 30;
        emojiLabel.setLayoutParams(lblLp);
        dialogLayout.addView(emojiLabel);

        android.widget.LinearLayout emojiRow = new android.widget.LinearLayout(this);
        emojiRow.setOrientation(LinearLayout.HORIZONTAL);
        emojiRow.setGravity(Gravity.CENTER);
        final android.widget.TextView[] emojiViews = new android.widget.TextView[emojis.length];

        for (int i = 0; i < emojis.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(emojis[i]);
            tv.setTextSize(28);
            tv.setPadding(16, 16, 16, 16);
            tv.setAlpha(i == selectedIdx[0] ? 1f : 0.4f);
            final int idx = i;
            tv.setOnClickListener(v -> {
                selectedIdx[0] = idx;
                for (int j = 0; j < emojiViews.length; j++) {
                    emojiViews[j].setAlpha(j == idx ? 1f : 0.4f);
                }
            });
            emojiViews[i] = tv;
            emojiRow.addView(tv);
        }

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = 20;
        emojiRow.setLayoutParams(rowLp);
        dialogLayout.addView(emojiRow);

        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dark)
            .setTitle("Edit Habit")
            .setView(dialogLayout)
            .setPositiveButton("Save", (d, w) -> {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty()) {
                    habit.name = newName;
                    habit.emoji = emojis[selectedIdx[0]];
                    storage.updateHabit(habit);
                    recreate();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
