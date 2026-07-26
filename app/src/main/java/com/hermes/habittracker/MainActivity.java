package com.hermes.habittracker;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
        "Motivation is what gets you started. Habit is what keeps you going.",
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
        "The man who moves a mountain begins by carrying away small stones."
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
                adapter.notifyItemChanged(pos);
                updateCount();
                StreaksWidgetProvider.updateAllWidgets(MainActivity.this);
            }
            @Override
            public void onLongClick(int pos) {
                showDeleteDialog(pos);
            }
        });
        list.setAdapter(adapter);

        FloatingActionButton addBtn = findViewById(R.id.addButton);
        addBtn.setOnClickListener(v -> showAddDialog());

        updateCount();
        showDailyQuote();

        if (habits.isEmpty()) {
            Toast.makeText(this, "Tap + to add your first habit!", Toast.LENGTH_LONG).show();
        }
    }

    private void showDailyQuote() {
        int dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        String quote = QUOTES[dayOfYear % QUOTES.length];
        TextView q = findViewById(R.id.dailyQuote);
        q.setText(quote);
        q.setVisibility(View.VISIBLE);
    }

    private void showDeleteDialog(int pos) {
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dark)
            .setTitle("Delete habit?")
            .setMessage(habits.get(pos).name)
            .setPositiveButton("Delete", (d, w) -> {
                storage.deleteHabit(habits.get(pos).id);
                habits.remove(pos);
                adapter.update(habits);
                updateCount();
                StreaksWidgetProvider.updateAllWidgets(this);
            })
            .setNegativeButton("Cancel", null)
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
        android.widget.LinearLayout emojiRow = dialogView.findViewById(R.id.emojiRow);
        selectedEmojiIdx = 0;

        android.widget.TextView[] emojiViews = new android.widget.TextView[emojis.length];
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
                adapter.notifyItemInserted(habits.size() - 1);
                updateCount();
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
}
