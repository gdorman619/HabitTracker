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
 *   - Long-press + → test data menu (load/clear)
 *   - Undo snackbar after toggling (5-second undo)
 *   - Empty state with welcome message when no habits
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
import com.google.android.material.snackbar.Snackbar;
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
                habits.get(pos).toggleToday();
                storage.updateHabit(habits.get(pos));
                sortHabits();
                adapter.update(habits);
                updateCount();
                StreaksWidgetProvider.updateAllWidgets(MainActivity.this);
                showUndoToast(pos);
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
        });
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
                .setNeutralButton("Cancel", null)
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
            empty.setText("\uD83D\uDD25 Ready to build some streaks?\n\nTap the + button to add your first habit!");
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setTextSize(16);
            empty.setTextColor(0xff9aa7b4);
            empty.setPadding(0, 100, 0, 0);
        } else {
            empty.setVisibility(View.GONE);
        }
    }

    private void showUndoToast(int pos) {
        View view = findViewById(android.R.id.content);
        Snackbar.make(view, "Habit toggled", Snackbar.LENGTH_SHORT)
            .setAction("Undo", v -> {
                habits.get(pos).toggleToday();
                storage.updateHabit(habits.get(pos));
                adapter.notifyItemChanged(pos);
                updateCount();
                StreaksWidgetProvider.updateAllWidgets(this);
            })
            .show();
    }

    private void showHabitMenu(int pos) {
        String[] options = {"View History", "Edit Habit", "Delete Habit"};
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dark)
            .setTitle(habits.get(pos).emoji + " " + habits.get(pos).name)
            .setItems(options, (d, which) -> {
                switch (which) {
                    case 0: // View History
                        Intent intent = new Intent(this, HabitDetailActivity.class);
                        intent.putExtra("habit_id", habits.get(pos).id);
                        startActivity(intent);
                        break;
                    case 1: // Edit
                        Intent editIntent = new Intent(this, HabitDetailActivity.class);
                        editIntent.putExtra("habit_id", habits.get(pos).id);
                        startActivity(editIntent);
                        break;
                    case 2: // Delete
                        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dark)
                            .setTitle("Delete habit?")
                            .setMessage(habits.get(pos).name)
                            .setPositiveButton("Delete", (d2, w) -> {
                                storage.deleteHabit(habits.get(pos).id);
                                habits.remove(pos);
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
        selectedEmojiIdx = 0;

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
                storage.addHabit(h);
                habits.add(h);
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

    private void updateCount() {
        TextView count = findViewById(R.id.habitCount);
        int done = 0;
        for (Habit h : habits) if (h.isDoneToday()) done++;
        count.setText(done + "/" + habits.size() + " today");
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
