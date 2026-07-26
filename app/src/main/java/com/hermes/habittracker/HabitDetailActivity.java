package com.hermes.habittracker;

/*
 * HabitDetailActivity.java — Detail/history screen for a single habit.
 *
 * Shows:
 *   - Header: emoji + habit name
 *   - Stats row: current streak (🔥) + total completions
 *   - Started badge: pill-style date when the habit was created
 *   - Freeze section: remaining freezes, "Use Freeze" button
 *   - Month calendar: navigable with ‹ › arrows (browse any month)
 *     - Green cells = completed
 *     - Blue cell = today
 *     - Amber cells = missed (after habit was created)
 *     - Ice-blue ❄️ = frozen day
 *     - Dark grey = before habit existed (N/A)
 *   - Legend: color key
 *   - Edit button: change name + emoji
 *   - Delete button: remove habit (with confirmation)
 *
 * The entire UI is built programmatically (no XML layout) for flexibility.
 */

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HabitDetailActivity extends AppCompatActivity {

    private HabitStorage storage;
    private Habit habit;
    private Calendar viewMonth = Calendar.getInstance();
    private LinearLayout calendarContainer;

    // === Color palette (ARGB hex) ===
    // Softer, more distinct colors for better visibility on dark background
    private static final int C_BG = 0xff0f1419;        // app background (navy)
    private static final int C_CARD = 0xff1a212b;      // card/section background
    private static final int C_ACCENT = 0xff4cc2ff;     // primary accent (cyan)
    private static final int C_TEXT = 0xffe6edf3;       // primary text
    private static final int C_MUTED = 0xff9aa7b4;      // secondary text
    private static final int C_DONE_BG = 0xff2ea043;    // completed day background (forest green)
    private static final int C_DONE_FG = 0xfff0f6fc;    // completed day text (near-white)
    private static final int C_TODAY_BG = 0xff1a4a6e;   // today background (deep blue)
    private static final int C_TODAY_FG = 0xff4cc2ff;   // today text (cyan)
    private static final int C_MISS_BG = 0xff3d2417;    // missed day background (warm brown)
    private static final int C_MISS_FG = 0xffdb6d28;    // missed day text (amber)
    private static final int C_FROZEN_BG = 0xff1a3850;  // frozen day background (ice blue)
    private static final int C_FROZEN_FG = 0xff7dd3fc;  // frozen day text (light cyan)
    private static final int C_NA_BG = 0xff11181f;      // before-creation background (very dark)
    private static final int C_NA_FG = 0x449aa7b4;      // before-creation text (faded)
    private static final int C_BORDER = 0xff2a3441;     // border/disabled background
    private static final int C_RED = 0xfff85149;        // delete/danger (red)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int habitId = getIntent().getIntExtra("habit_id", -1);
        if (habitId == -1) { finish(); return; }

        storage = new HabitStorage(this);
        reloadHabit(habitId);
        if (habit == null) { finish(); return; }

        viewMonth.set(Calendar.DAY_OF_MONTH, 1);
        buildUI();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void reloadHabit(int habitId) {
        List<Habit> habits = storage.loadHabits();
        for (Habit h : habits) {
            if (h.id == habitId) { habit = h; break; }
        }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        int pad = dp(20);
        root.setPadding(pad, dp(48), pad, pad);

        // Header
        TextView header = new TextView(this);
        header.setText(habit.emoji + "  " + habit.name);
        header.setTextSize(24);
        header.setTextColor(C_ACCENT);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, dp(8));
        root.addView(header);

        // Streak + total row
        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setGravity(Gravity.CENTER_VERTICAL);

        int streak = habit.getStreak();
        TextView streakTv = new TextView(this);
        streakTv.setText(streak > 0 ? "\uD83D\uDD25 " + streak + " day streak" : "No current streak");
        streakTv.setTextSize(15);
        streakTv.setTextColor(streak > 0 ? 0xffd29922 : C_MUTED);
        statsRow.addView(streakTv);

        TextView sep = new TextView(this);
        sep.setText("  \u2022  ");
        sep.setTextColor(C_MUTED);
        sep.setTextSize(15);
        statsRow.addView(sep);

        TextView totalTv = new TextView(this);
        totalTv.setText(habit.completedDates.size() + " total");
        totalTv.setTextSize(15);
        totalTv.setTextColor(C_MUTED);
        statsRow.addView(totalTv);

        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.bottomMargin = dp(4);
        statsRow.setLayoutParams(sLp);
        root.addView(statsRow);

        // Started badge — pill style
        if (habit.createdAt != null) {
            TextView startedPill = new TextView(this);
            startedPill.setText("  Started " + habit.createdAt + "  ");
            startedPill.setTextSize(12);
            startedPill.setTextColor(C_MUTED);
            startedPill.setBackgroundColor(0xff11181f);
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            pLp.bottomMargin = dp(16);
            startedPill.setLayoutParams(pLp);
            startedPill.setPadding(dp(12), dp(6), dp(12), dp(6));
            root.addView(startedPill);
        }

        // Freeze section
        addFreezeSection(root);

        // Month navigation header
        LinearLayout monthHeader = new LinearLayout(this);
        monthHeader.setOrientation(LinearLayout.HORIZONTAL);
        monthHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mhLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mhLp.bottomMargin = dp(12);
        monthHeader.setLayoutParams(mhLp);

        TextView prevBtn = new TextView(this);
        prevBtn.setText("  \u2039  ");
        prevBtn.setTextSize(22);
        prevBtn.setTextColor(C_ACCENT);
        prevBtn.setGravity(Gravity.CENTER);
        prevBtn.setOnClickListener(v -> { viewMonth.add(Calendar.MONTH, -1); rebuildCalendar(); });

        TextView monthLabel = new TextView(this);
        monthLabel.setId(android.R.id.text1);
        monthLabel.setGravity(Gravity.CENTER);
        monthLabel.setTextSize(17);
        monthLabel.setTextColor(C_TEXT);
        monthLabel.setTypeface(monthLabel.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams mlLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        mlLp.gravity = Gravity.CENTER;
        monthLabel.setLayoutParams(mlLp);

        TextView nextBtn = new TextView(this);
        nextBtn.setText("  \u203A  ");
        nextBtn.setTextSize(22);
        nextBtn.setTextColor(C_ACCENT);
        nextBtn.setGravity(Gravity.CENTER);
        nextBtn.setOnClickListener(v -> { viewMonth.add(Calendar.MONTH, 1); rebuildCalendar(); });

        monthHeader.addView(prevBtn);
        monthHeader.addView(monthLabel);
        monthHeader.addView(nextBtn);
        root.addView(monthHeader);

        // Calendar container (rebuilt on month change)
        calendarContainer = new LinearLayout(this);
        calendarContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(calendarContainer);
        rebuildCalendar();

        // Legend
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setPadding(0, dp(12), 0, dp(16));
        addLegend(legend, C_DONE_BG, "Done");
        addLegend(legend, C_TODAY_BG, "Today");
        addLegend(legend, C_MISS_BG, "Missed");
        addLegend(legend, C_FROZEN_BG, "\u2744 Frozen");
        addLegend(legend, C_NA_BG, "N/A");
        root.addView(legend);

        // Edit + Delete buttons
        addButton(root, "  Edit Habit  ", C_ACCENT, C_BG, v -> showEditDialog());
        addButton(root, "  Delete Habit  ", C_RED, 0, v -> {
            new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dark)
                .setTitle("Delete habit?")
                .setMessage(habit.name)
                .setPositiveButton("Delete", (d, w) -> { storage.deleteHabit(habit.id); finish(); })
                .setNegativeButton("Cancel", null)
                .show();
        });

        scroll.addView(root);
        setContentView(scroll);
    }

    private void addButton(LinearLayout root, String text, int bg, int fg, View.OnClickListener listener) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(15);
        btn.setTextColor(fg == 0 ? C_RED : fg);
        btn.setBackgroundColor(bg);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        root.addView(btn);
    }

    private void addFreezeSection(LinearLayout root) {
        boolean alreadyFrozen = habit.freezesUsed > 0;
        int remaining = storage.getFreezesRemaining();
        int max = storage.getMaxFreezes();
        boolean canFreeze = storage.canFreezeHabit(habit);

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackgroundColor(C_CARD);
        section.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.bottomMargin = dp(16);
        section.setLayoutParams(sLp);

        TextView freezeText = new TextView(this);
        if (alreadyFrozen) {
            freezeText.setText("\u2744\uFE0F Freeze active on this habit");
        } else {
            freezeText.setText("\u2744\uFE0F Streak Freezes: " + remaining + "/" + max + " remaining");
        }
        freezeText.setTextSize(14);
        freezeText.setTextColor(C_MUTED);
        section.addView(freezeText);

        TextView desc = new TextView(this);
        desc.setText(alreadyFrozen ? "Your streak is protected from 1 missed day." : "Freezes protect your streak when you miss a day.");
        desc.setTextSize(12);
        desc.setTextColor(0x889aa7b4);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(4);
        desc.setLayoutParams(dLp);
        section.addView(desc);

        TextView btn = new TextView(this);
        if (alreadyFrozen) {
            btn.setText("  \u2744 Already Frozen  ");
            btn.setTextColor(C_MUTED);
            btn.setBackgroundColor(C_BORDER);
        } else if (canFreeze) {
            btn.setText("  \u2744 Use Freeze  ");
            btn.setTextColor(C_BG);
            btn.setBackgroundColor(C_ACCENT);
        } else {
            btn.setText(remaining > 0 ? "  Already Used  " : "  No Freezes Left  ");
            btn.setTextColor(C_MUTED);
            btn.setBackgroundColor(C_BORDER);
        }
        btn.setTextSize(14);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bLp.topMargin = dp(10);
        btn.setLayoutParams(bLp);
        if (canFreeze) {
            btn.setOnClickListener(v -> {
                if (storage.freezeHabit(habit)) {
                    Toast.makeText(this, "\u2744\uFE0F Freeze applied!", Toast.LENGTH_SHORT).show();
                    recreate();
                }
            });
        }
        section.addView(btn);
        root.addView(section);
    }

    private void rebuildCalendar() {
        calendarContainer.removeAllViews();
        TextView monthLabel = findViewById(android.R.id.text1);
        SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM yyyy", Locale.US);
        monthLabel.setText(monthFmt.format(viewMonth.getTime()));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String todayStr = sdf.format(new Date());

        // Day labels
        String[] dayLabels = {"S", "M", "T", "W", "T", "F", "S"};
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int d = 0; d < 7; d++) {
            TextView lbl = new TextView(this);
            lbl.setText(dayLabels[d]);
            lbl.setTextColor(C_MUTED);
            lbl.setTextSize(11);
            lbl.setGravity(Gravity.CENTER);
            lbl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            labelRow.addView(lbl);
        }
        calendarContainer.addView(labelRow);

        // Build calendar for viewMonth
        Calendar cal = (Calendar) viewMonth.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; // Sunday = 0
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Leading empty cells
        int totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7;
        int cellSize = dp(38);
        int margin = dp(2);

        for (int i = 0; i < totalCells; i++) {
            if (i % 7 == 0) {
                LinearLayout weekRow = new LinearLayout(this);
                weekRow.setOrientation(LinearLayout.HORIZONTAL);
                weekRow.setPadding(0, margin, 0, margin);
                weekRow.setTag("week" + (i / 7));
                calendarContainer.addView(weekRow);
            }
            LinearLayout weekRow = (LinearLayout) calendarContainer.getChildAt(calendarContainer.getChildCount() - 1);

            int dayNum = i - firstDayOfWeek + 1;
            TextView day = new TextView(this);

            if (dayNum < 1 || dayNum > daysInMonth) {
                day.setText("");
                day.setBackgroundColor(0x00000000);
            } else {
                cal.set(Calendar.DAY_OF_MONTH, dayNum);
                String dateStr = sdf.format(cal.getTime());
                boolean completed = habit.completedDates.contains(dateStr);
                boolean frozen = habit.frozenDates.contains(dateStr);
                boolean isToday = dateStr.equals(todayStr);
                boolean isFuture = cal.getTimeInMillis() > System.currentTimeMillis() + 43200000;
                boolean beforeStart = habit.createdAt != null && dateStr.compareTo(habit.createdAt) < 0;

                day.setText(String.valueOf(dayNum));
                day.setTextSize(13);
                day.setGravity(Gravity.CENTER);

                if (isToday) {
                    day.setTextColor(C_TODAY_FG);
                    day.setBackgroundColor(C_TODAY_BG);
                    day.setTypeface(day.getTypeface(), android.graphics.Typeface.BOLD);
                } else if (completed) {
                    day.setTextColor(C_DONE_FG);
                    day.setBackgroundColor(C_DONE_BG);
                } else if (frozen) {
                    day.setTextColor(C_FROZEN_FG);
                    day.setBackgroundColor(C_FROZEN_BG);
                    day.setText("\u2744");
                } else if (isFuture || beforeStart) {
                    day.setTextColor(C_NA_FG);
                    day.setBackgroundColor(C_NA_BG);
                } else {
                    day.setTextColor(C_MISS_FG);
                    day.setBackgroundColor(C_MISS_BG);
                }
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cellSize, 1);
            lp.setMargins(margin, margin, margin, margin);
            day.setLayoutParams(lp);
            weekRow.addView(day);
        }
    }

    private void addLegend(LinearLayout legend, int color, String label) {
        TextView dot = new TextView(this);
        dot.setText("  ");
        dot.setBackgroundColor(color);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(14), dp(14));
        dotLp.setMarginEnd(dp(4));
        dot.setLayoutParams(dotLp);
        legend.addView(dot);

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(C_MUTED);
        text.setTextSize(11);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tLp.setMarginEnd(dp(12));
        text.setLayoutParams(tLp);
        legend.addView(text);
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
        dialogLayout.setPadding(dp(40), dp(30), dp(40), dp(15));

        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(habit.name);
        input.setTextColor(C_TEXT);
        input.setTextSize(16);
        input.setHint("Habit name");
        input.setHintTextColor(C_MUTED);
        dialogLayout.addView(input);

        TextView emojiLabel = new TextView(this);
        emojiLabel.setText("Pick an emoji:");
        emojiLabel.setTextColor(C_MUTED);
        emojiLabel.setTextSize(14);
        LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lblLp.topMargin = dp(20);
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
            tv.setPadding(dp(16), dp(16), dp(16), dp(16));
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
        rowLp.topMargin = dp(15);
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
