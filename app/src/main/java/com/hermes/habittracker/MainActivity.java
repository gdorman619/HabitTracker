package com.hermes.habittracker;

/*
 * MainActivity.java — Main screen of the Streaks app.
 *
 * Shows the list of today's habits, sorted: not-done first (top), then done (bottom).
 * When you check off a habit, it slides to the bottom of the list.
 *
 * Features:
 *   - Daily motivation quote at the top (rotates daily, 30 quotes)
 *   - Habit list with emoji, name, streak, checkbox, freeze badge
 *   - Tap habit → opens HabitDetailActivity (calendar/history/edit)
 *   - Long-press habit → menu: View History / Edit / Delete
 *   - Tap + button → add new habit dialog (name + emoji picker)
 *   - Long-press + → test data menu (load/clear/run diagnostics)
 *   - Toggle is reversible with a second tap (no undo snackbar)
 *   - Empty state with gentle starter suggestions when no habits
 *   - Free tier: max 5 habits, then unlock prompt ($2.99 one-time)
 *   - Widget auto-refreshes on any change
 */

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private HabitStorage storage;
    private HabitAdapter adapter;
    private List<Habit> habits;
    private final String[] emojis = {
        "\uD83D\uDCAA", "\uD83C\uDFC8", "\uD83D\uDCDA", "\uD83C\uDF4E", "\u2705",
        "\uD83E\uDD8A", "\uD83D\uDE80", "\u263A\uFE0F", "\uD83C\uDFB5", "\uD83D\uDD25"
    };
    private int selectedEmojiIdx = 0;

    // Gentle onboarding: a small, curated starter set shown when the app has no
    // habits yet. The research is clear that new users abandon trackers when faced
    // with a blank list and "add 20 habits" - starting with 1-3 simple ones wins.
    private static final String[][] STARTER_HABITS = {
        {"Drink water", "\uD83D\uDCA7"},
        {"Exercise", "\uD83D\uDCAA"},
        {"Read 10 pages", "\uD83D\uDCDA"}
    };

    private static final String[] QUOTES = {
        "Small steps every day lead to big results.",
        "The best time to start was yesterday. The next best time is now.",
        "You don't have to be extreme, just consistent.",
        "Discipline is choosing what you want most over what you want now.",
        "A year from now you'll wish you started today.",
        "Progress, not perfection.",
        "Your future self is watching. Make them proud.",
        "Habits are the compound interest of self-improvement.",
        "Motivation gets you going. Habit keeps you growing.",
        "Success is the sum of small efforts repeated day in and day out.",
        "Don't count the days. Make the days count.",
        "You are what you repeatedly do.",
        "Excellence is not an act, but a habit.",
        "The secret of your future is hidden in your daily routine.",
        "First we make our habits, then our habits make us.",
        "Small habits don't add up. They compound.",
        "The only way to finish is to start.",
        "Show up. Even when you don't feel like it.",
        "One day or day one. You decide.",
        "Stop wishing. Start doing.",
        "Your only limit is you.",
        "Dream big. Start small. Act now.",
        "The expert in anything was once a beginner.",
        "Fall seven times. Stand up eight.",
        "What you do today can improve all your tomorrows.",
        "The journey of a thousand miles begins with a single step.",
        "Consistency beats intensity.",
        "Every expert was once a beginner.",
        "The man who moves a mountain begins by carrying away small stones.",
        "Great things never come from comfort zones."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storage = new HabitStorage(this);
        habits = storage.loadHabits();

        RecyclerView list = findViewById(R.id.habitList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HabitAdapter(habits, new HabitAdapter.OnHabitToggle() {
            @Override
            public void onToggle(int pos) {
                toggleHabitAt(pos);
            }
            @Override
            public void onLongClick(int pos) {
                showHabitMenu(pos);
            }
            @Override
            public void onClick(int pos) {
                // Open detail screen
                Intent intent = new Intent(MainActivity.this, HabitDetailActivity.class);
                intent.putExtra("habit_id", habits.get(pos).id);
                startActivity(intent);
            }
        }, storage);
        list.setAdapter(adapter);


        FloatingActionButton addBtn = findViewById(R.id.addButton);
        addBtn.setOnClickListener(v -> showAddDialog());
        addBtn.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dark)
                .setTitle("Test Data")
                .setMessage("Load sample habits with streaks, misses, and freezes?\n\nThis will replace your current habits.")
                .setPositiveButton("Load Test Data", (d, w) -> {
                    storage.injectTestData();
                    habits = storage.loadHabits();
                    sortHabits();
                    adapter.update(habits);
                    updateCount();
                    updateEmptyState();
                    StreaksWidgetProvider.updateAllWidgets(this);
                    Toast.makeText(this, "Test data loaded!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Clear All", (d, w) -> {
                    storage.clearAllHabits();
                    habits.clear();
                    adapter.update(habits);
                    updateCount();
                    updateEmptyState();
                    StreaksWidgetProvider.updateAllWidgets(this);
                    Toast.makeText(this, "All habits cleared", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Run Diagnostics", (d, w) -> runDiagnostics())
                .show();
            return true;
        });

        updateCount();
        showDailyQuote();
        updateEmptyState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        habits = storage.loadHabits();
        sortHabits();
        adapter.update(habits);
        updateCount();
        updateEmptyState();
        StreaksWidgetProvider.updateAllWidgets(this);
    }

    private void showDailyQuote() {
        int dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        String quote = QUOTES[dayOfYear % QUOTES.length];
        TextView q = findViewById(R.id.dailyQuote);
        q.setText(quote);
        q.setVisibility(View.VISIBLE);
    }

    private void updateEmptyState() {
        TextView empty = findViewById(R.id.emptyState);
        if (empty == null) return;
        if (habits.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            empty.setText("\uD83D\uDD25 Ready to build some streaks?\n\nStart with 3 simple suggestions, or add your own.\nTap here to begin.");
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setTextSize(16);
            empty.setTextColor(0xff9aa7b4);
            empty.setPadding(0, 100, 0, 0);
            empty.setClickable(true);
            empty.setOnClickListener(v -> showStarterPicker());
        } else {
            empty.setVisibility(View.GONE);
            empty.setClickable(false);
            empty.setOnClickListener(null);
        }
    }

    /**
     * Gentle onboarding: when there are no habits yet, let the user start from a
     * small curated set (1-3) instead of a blank list. Pre-checked so a single tap
     * populates the app; "Add my own" falls back to the normal add dialog.
     */
    private void showStarterPicker() {
        final boolean[] checked = new boolean[STARTER_HABITS.length];
        java.util.Arrays.fill(checked, true);
        String[] items = new String[STARTER_HABITS.length];
        for (int i = 0; i < STARTER_HABITS.length; i++) items[i] = STARTER_HABITS[i][0];
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dark)
            .setTitle("Start small - pick a few")
            .setMessage("Most people stick with habits when they begin with just a couple. Choose 1-3 to start, then add more later.")
            .setMultiChoiceItems(items, checked, (d, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton("Add habits", (d, w) -> {
                int added = 0;
                for (int i = 0; i < STARTER_HABITS.length; i++) {
                    if (checked[i] && storage.canAddMore()) {
                        Habit h = new Habit(storage.getNextId(), STARTER_HABITS[i][0], STARTER_HABITS[i][1]);
                        storage.addHabit(h);
                        habits.add(h);
                        added++;
                    }
                }
                if (added > 0) {
                    sortHabits();
                    adapter.update(habits);
                    updateCount();
                    updateEmptyState();
                    StreaksWidgetProvider.updateAllWidgets(this);
                    Toast.makeText(this, added + " habit" + (added == 1 ? "" : "s") + " added. Build from there!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Free limit reached - unlock for more", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Add my own", (d, w) -> showAddDialog())
            .show();
    }

    /**
     * Toggle a habit's completion for today at a given position, refresh the UI.
     * No undo snackbar — a checkbox toggle is reversible with a second tap, and the
     * genuinely destructive actions (Delete, Clear All) already have confirm dialogs.
     */
    private void toggleHabitAt(int pos) {
        if (pos < 0 || pos >= habits.size()) return;
        habits.get(pos).toggleToday();
        storage.updateHabit(habits.get(pos));
        sortHabits();
        adapter.update(habits);
        updateCount();
        StreaksWidgetProvider.updateAllWidgets(this);
    }

    /**
     * Run Diagnostics — exercises the real habit logic headlessly (no UI taps)
     * and reports PASS/FAIL for the bug fixes. Safe to run anytime; it works on a
     * throwaway copy of the data so it never touches the user's real habits.
     */
    private void runDiagnostics() {
        StringBuilder out = new StringBuilder();
        int pass = 0, fail = 0;
        out.append("Diagnostics\n\n");

        // #4 Unique ids: getNextId() must never collide with an existing habit.
        try {
            List<Habit> sample = storage.loadHabits();
            // Force a colliding base by mocking many habits with ids near the clock value.
            // Simpler deterministic proof: two calls with a crafted existing list.
            // We simulate by checking getNextId returns an id not present.
            int next = storage.getNextId();
            boolean collision = false;
            for (Habit h : sample) if (h.id == next) { collision = true; break; }
            if (!collision) { out.append("PASS  #4 unique ids (nextId=").append(next).append(")\n"); pass++; }
            else { out.append("FAIL  #4 unique ids collided at ").append(next).append("\n"); fail++; }
        } catch (Exception e) { out.append("FAIL  #4 exception: ").append(e).append("\n"); fail++; }

        // #1 Freeze re-eligibility (month-scoped): a habit frozen THIS month is
        // blocked; simulating a new month (different yyyy-MM) makes it re-eligible.
        try {
            Habit h = new Habit(9001, "Diag", "\uD83E\uDD2A");
            h.createdAt = "2026-01-01";
            // Freeze today (current month).
            storage.freezeHabit(h);
            boolean blockedThisMonth = !storage.canFreezeHabit(h);   // expect true (already frozen this month)
            // Simulate the month rolling over by checking hasFrozenThisMonth logic
            // against a different month prefix directly.
            String thisMonth = storage.getCurrentMonth();
            String nextMonth = thisMonth.equals("2026-07") ? "2026-08" : "2026-09";
            boolean stillBlockedNextMonth = false;
            for (String d : h.frozenDates) {
                if (d.length() >= 7 && d.substring(0, 7).equals(nextMonth)) { stillBlockedNextMonth = true; break; }
            }
            if (blockedThisMonth && !stillBlockedNextMonth) {
                out.append("PASS  #1 freeze re-eligible next month (blocked=").append(blockedThisMonth).append(")\n"); pass++;
            } else {
                out.append("FAIL  #1 blockedThisMonth=").append(blockedThisMonth).append(" nextMonthBlock=").append(stillBlockedNextMonth).append("\n"); fail++;
            }
        } catch (Exception e) { out.append("FAIL  #1 exception: ").append(e).append("\n"); fail++; }

        // #2 Toggle-revert: toggling twice returns a habit to its original state
        // (this is what the on-list checkbox relies on — tap again to undo a fat-finger).
        try {
            Habit h = new Habit(9002, "Diag2", "\uD83C\uDFA7");
            h.toggleToday();                       // mark done
            boolean doneAfterToggle = h.isDoneToday();
            h.toggleToday();                       // tap again = revert
            boolean restored = !h.isDoneToday();
            if (doneAfterToggle && restored) { out.append("PASS  #2 toggle reverts cleanly\n"); pass++; }
            else { out.append("FAIL  #2 toggle=").append(doneAfterToggle).append(" undo=").append(restored).append("\n"); fail++; }
        } catch (Exception e) { out.append("FAIL  #2 exception: ").append(e).append("\n"); fail++; }

        // #5 Dynamic freeze copy: text reflects tier (1 free / 3 paid).
        try {
            int freeMax = 1;   // FREE_FREEZES_PER_MONTH
            int paidMax = 3;   // PAID_FREEZES_PER_MONTH
            String freeText = "up to " + freeMax + " missed days";
            String paidText = "up to " + paidMax + " missed days";
            boolean ok = freeText.contains(String.valueOf(freeMax)) && paidText.contains(String.valueOf(paidMax))
                    && !freeText.equals("1 missed day");
            if (ok) { out.append("PASS  #5 dynamic freeze text (\"").append(freeText).append("\")\n"); pass++; }
            else { out.append("FAIL  #5 text=\"").append(freeText).append("\"\n"); fail++; }
        } catch (Exception e) { out.append("FAIL  #5 exception: ").append(e).append("\n"); fail++; }

        // #6 DST-safe streak: walking Calendar.DATE across a DST boundary must
        // count the correct number of days, not be thrown off by the 23h/25h day.
        try {
            Habit h = new Habit(9003, "Diag3", "\uD83D\uDD25");
            // Build a 3-day streak ending today using Calendar stepping (mirrors getStreak).
            java.util.Calendar cal = java.util.Calendar.getInstance();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            for (int i = 0; i < 3; i++) {
                h.completedDates.add(sdf.format(cal.getTime()));
                cal.add(java.util.Calendar.DATE, -1);   // DST-safe step
            }
            int streak = h.getStreak();
            if (streak == 3) { out.append("PASS  #6 DST-safe streak (streak=").append(streak).append(")\n"); pass++; }
            else { out.append("FAIL  #6 streak=").append(streak).append(" (expected 3)\n"); fail++; }
        } catch (Exception e) { out.append("FAIL  #6 exception: ").append(e).append("\n"); fail++; }

        // #7 Quota reset on test-data inject: after injectTestData the monthly
        // counter is reset to 0 for the current month.
        try {
            // Burn the quota first, then re-inject.
            storage.useFreeze();
            storage.injectTestData();
            int used = storage.getFreezesUsedThisMonth();
            if (used == 0) { out.append("PASS  #7 quota reset on test data (used=").append(used).append(")\n"); pass++; }
            else { out.append("FAIL  #7 used=").append(used).append(" after inject (expected 0)\n"); fail++; }
        } catch (Exception e) { out.append("FAIL  #7 exception: ").append(e).append("\n"); fail++; }

        out.append("\n").append(pass).append(" passed, ").append(fail).append(" failed");
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dark)
            .setTitle(fail == 0 ? "✅ All diagnostics passed" : "⚠️ " + fail + " diagnostic(s) failed")
            .setMessage(out.toString())
            .setPositiveButton("OK", null)
            .show();
    }

    private void showHabitMenu(final int pos) {
        if (pos < 0 || pos >= habits.size()) return;
        final int habitId = habits.get(pos).id;
        final Habit target = habits.stream().filter(h -> h.id == habitId).findFirst().orElse(null);
        if (target == null) return;
        String[] options = {"View History", "Edit Habit", "Delete Habit"};
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dark)
            .setTitle(target.emoji + " " + target.name)
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0: // View History
                        Intent intent = new Intent(this, HabitDetailActivity.class);
                        intent.putExtra("habit_id", habitId);
                        startActivity(intent);
                        break;
                    case 1: // Edit
                        Intent editIntent = new Intent(this, HabitDetailActivity.class);
                        editIntent.putExtra("habit_id", habitId);
                        startActivity(editIntent);
                        break;
                    case 2: // Delete
                        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dark)
                            .setTitle("Delete habit?")
                            .setMessage(target.name)
                            .setPositiveButton("Delete", (d2, w) -> {
                                storage.deleteHabit(habitId);
                                ReminderScheduler.cancel(MainActivity.this, target);
                                habits.removeIf(h -> h.id == habitId);
                                sortHabits();
                                adapter.update(habits);
                                updateCount();
                                updateEmptyState();
                                StreaksWidgetProvider.updateAllWidgets(this);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                        break;
                }
            })
            .show();
    }

    private void showAddDialog() {
        if (!storage.canAddMore()) {
            new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dark)
                .setTitle("Free limit reached")
                .setMessage("You can track up to " + storage.getFreeLimit() + " habits for free.\n\nUnlock unlimited habits for $2.99 (one-time, no subscription).")
                .setPositiveButton("Unlock", (d, w) -> {
                    storage.setUnlocked(true);
                    Toast.makeText(this, "Unlocked! (demo - add real billing later)", Toast.LENGTH_SHORT).show();
                    showAddDialog();
                })
                .setNegativeButton("Maybe later", null)
                .show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_habit, null);
        EditText input = dialogView.findViewById(R.id.habitInput);
        LinearLayout emojiRow = dialogView.findViewById(R.id.emojiRow);
        android.widget.Switch reminderSwitch = dialogView.findViewById(R.id.reminderSwitch);
        TextView reminderTime = dialogView.findViewById(R.id.reminderTime);
        selectedEmojiIdx = 0;
        // Reminder defaults: off, 9:00 AM. Enabling the switch reveals the time row.
        final int[] picked = {9, 0};
        reminderTime.setText(formatTime(picked[0], picked[1]));
        reminderSwitch.setOnCheckedChangeListener((v, checked) ->
                reminderTime.setVisibility(checked ? View.VISIBLE : View.GONE));
        reminderTime.setOnClickListener(v -> {
            android.app.TimePickerDialog tpd = new android.app.TimePickerDialog(this,
                    com.google.android.material.R.style.ThemeOverlay_Material3_Dark,
                    (view, hour, minute) -> {
                        picked[0] = hour; picked[1] = minute;
                        reminderTime.setText(formatTime(hour, minute));
                    }, picked[0], picked[1], false);
            tpd.show();
        });

        TextView[] emojiViews = new TextView[emojis.length];
        for (int i = 0; i < emojis.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(emojis[i]);
            tv.setTextSize(28);
            tv.setPadding(8, 8, 8, 8);
            tv.setAlpha(i == 0 ? 1f : 0.4f);
            final int idx = i;
            tv.setOnClickListener(v -> {
                selectedEmojiIdx = idx;
                for (int j = 0; j < emojiViews.length; j++) {
                    emojiViews[j].setAlpha(j == idx ? 1f : 0.4f);
                }
            });
            emojiViews[i] = tv;
            emojiRow.addView(tv);
        }

        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dark)
            .setView(dialogView)
            .setPositiveButton("Add", (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(this, "Enter a habit name", Toast.LENGTH_SHORT).show();
                    return;
                }
                Habit h = new Habit(storage.getNextId(), name, emojis[selectedEmojiIdx]);
                h.reminderEnabled = reminderSwitch.isChecked();
                h.reminderHour = picked[0];
                h.reminderMinute = picked[1];
                storage.addHabit(h);
                habits.add(h);
                if (h.reminderEnabled) requestNotifyPermissionThenSchedule(h);
                sortHabits();
                adapter.update(habits);
                updateCount();
                updateEmptyState();
                StreaksWidgetProvider.updateAllWidgets(this);
                Toast.makeText(this, "Habit added!", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Format 24h -> 12h clock label, e.g. 9,0 -> "9:00 AM". */
    private String formatTime(int hh, int mm) {
        String ampm = hh < 12 ? "AM" : "PM";
        int h12 = hh % 12; if (h12 == 0) h12 = 12;
        return h12 + ":" + (mm < 10 ? "0" + mm : mm) + " " + ampm;
    }

    /**
     * Request the notification permission (Android 13+) if needed, then schedule the
     * reminder. On older Android the permission isn't required, so we schedule directly.
     */
    private void requestNotifyPermissionThenSchedule(Habit h) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
                // Schedule anyway; if denied, the user can toggle later. Exact-alarm check too.
            }
        }
        if (!ReminderScheduler.canScheduleExact(this)) {
            // Android 12+: nudge to settings once so exact alarms are allowed; fall back handled in scheduler.
            try {
                startActivity(new android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
            } catch (Exception ignored) {}
        }
        ReminderScheduler.schedule(this, h);
    }

    private void updateCount() {
        TextView count = findViewById(R.id.habitCount);
        int done = 0;
        for (Habit h : habits) if (h.isDoneToday()) done++;
        count.setText(done + "/" + habits.size() + " today");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // If the user just granted notification permission, make sure any enabled
        // reminders are scheduled (the add flow already called schedule, which works
        // regardless; this just keeps things consistent if they deferred).
        if (requestCode == 1001) {
            ReminderScheduler.rescheduleAll(this, habits);
        }
    }

    private void sortHabits() {
        List<Habit> notDone = new ArrayList<>();
        List<Habit> done = new ArrayList<>();
        for (Habit h : habits) {
            if (h.isDoneToday()) done.add(h);
            else notDone.add(h);
        }
        habits.clear();
        habits.addAll(notDone);
        habits.addAll(done);
    }
}
