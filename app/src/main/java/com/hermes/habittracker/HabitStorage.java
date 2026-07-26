package com.hermes.habittracker;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public class HabitStorage {
    private static final String PREFS = "habit_storage";
    private static final String KEY_HABITS = "habits_json";
    private static final String KEY_UNLOCKED = "is_unlocked";
    private static final String KEY_FREEZE_MONTH = "freeze_month";
    private static final String KEY_FREEZES_USED = "freezes_used_global";
    private static final int FREE_LIMIT = 5;
    private static final int FREE_FREEZES_PER_MONTH = 1;
    private static final int PAID_FREEZES_PER_MONTH = 3;

    private final SharedPreferences prefs;

    public HabitStorage(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Habit> loadHabits() {
        List<Habit> habits = new ArrayList<>();
        String json = prefs.getString(KEY_HABITS, "");
        if (json.isEmpty()) return habits;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                Habit h = Habit.fromJson(arr.getString(i));
                if (h != null) habits.add(h);
            }
        } catch (Exception e) {}
        return habits;
    }

    public void saveHabits(List<Habit> habits) {
        JSONArray arr = new JSONArray();
        for (Habit h : habits) arr.put(h.toJson());
        prefs.edit().putString(KEY_HABITS, arr.toString()).apply();
    }

    public void addHabit(Habit habit) {
        List<Habit> habits = loadHabits();
        habits.add(habit);
        saveHabits(habits);
    }

    public void deleteHabit(int id) {
        List<Habit> habits = loadHabits();
        habits.removeIf(h -> h.id == id);
        saveHabits(habits);
    }

    public void updateHabit(Habit updated) {
        List<Habit> habits = loadHabits();
        for (int i = 0; i < habits.size(); i++) {
            if (habits.get(i).id == updated.id) {
                habits.set(i, updated);
                break;
            }
        }
        saveHabits(habits);
    }

    public boolean canAddMore() {
        return loadHabits().size() < FREE_LIMIT || isUnlocked();
    }

    public int getFreeLimit() { return FREE_LIMIT; }

    public boolean isUnlocked() {
        return prefs.getBoolean(KEY_UNLOCKED, false);
    }

    public void setUnlocked(boolean unlocked) {
        prefs.edit().putBoolean(KEY_UNLOCKED, unlocked).apply();
    }

    public int getNextId() {
        return (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
    }

    // === Freeze logic ===
    // Global monthly limit: how many total freezes you can use across ALL habits per month
    // Per-habit: each habit's freezesUsed counts how many missed days it tolerates in its streak

    public int getMaxFreezes() {
        return isUnlocked() ? PAID_FREEZES_PER_MONTH : FREE_FREEZES_PER_MONTH;
    }

    public String getCurrentMonth() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US);
        return sdf.format(new java.util.Date());
    }

    public int getFreezesUsedThisMonth() {
        String month = getCurrentMonth();
        String stored = prefs.getString(KEY_FREEZE_MONTH, "");
        if (!month.equals(stored)) {
            prefs.edit().putString(KEY_FREEZE_MONTH, month).putInt(KEY_FREEZES_USED, 0).apply();
            return 0;
        }
        return prefs.getInt(KEY_FREEZES_USED, 0);
    }

    public boolean canUseFreeze() {
        return getFreezesUsedThisMonth() < getMaxFreezes();
    }

    public void useFreeze() {
        // Increments global monthly counter
        prefs.edit().putInt(KEY_FREEZES_USED, getFreezesUsedThisMonth() + 1).apply();
    }

    public int getFreezesRemaining() {
        return getMaxFreezes() - getFreezesUsedThisMonth();
    }

    // Check if a specific habit can be frozen (hasn't been frozen yet, and global limit not reached)
    public boolean canFreezeHabit(Habit h) {
        return h.freezesUsed == 0 && canUseFreeze();
    }

    // Apply freeze to a specific habit
    public boolean freezeHabit(Habit h) {
        if (!canFreezeHabit(h)) return false;
        h.freezesUsed = 1;
        updateHabit(h);
        useFreeze();
        return true;
    }

    // === Test data injection ===
    public void injectTestData() {
        List<Habit> habits = new ArrayList<>();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        long now = System.currentTimeMillis();
        long day = 86400000L;

        // Habit 1: "Drink Water" - 10 day unbroken streak (done every day)
        Habit h1 = new Habit(1001, "Drink Water", "\uD83D\uDCA7");
        h1.createdAt = sdf.format(new java.util.Date(now - 11 * day));
        for (int i = 0; i < 10; i++) {
            h1.completedDates.add(sdf.format(new java.util.Date(now - i * day)));
        }
        habits.add(h1);

        // Habit 2: "Exercise" - 5 day streak but missed 2 days ago (would be 7 without the miss)
        Habit h2 = new Habit(1002, "Exercise", "\uD83D\uDCAA");
        h2.createdAt = sdf.format(new java.util.Date(now - 8 * day));
        for (int i = 0; i < 5; i++) {
            h2.completedDates.add(sdf.format(new java.util.Date(now - i * day)));
        }
        habits.add(h2);

        // Habit 3: "Read" - 7 day streak, missed 3 days ago, has a freeze applied
        Habit h3 = new Habit(1003, "Read 20 Pages", "\uD83D\uDCDA");
        h3.createdAt = sdf.format(new java.util.Date(now - 10 * day));
        for (int i = 0; i < 7; i++) {
            if (i == 3) continue;
            h3.completedDates.add(sdf.format(new java.util.Date(now - i * day)));
        }
        h3.freezesUsed = 1;
        habits.add(h3);

        // Habit 4: "Meditate" - just started, 2 day streak
        Habit h4 = new Habit(1004, "Meditate", "\uD83D\uDD25");
        h4.createdAt = sdf.format(new java.util.Date(now - 2 * day));
        for (int i = 0; i < 2; i++) {
            h4.completedDates.add(sdf.format(new java.util.Date(now - i * day)));
        }
        habits.add(h4);

        // Habit 5: "No Sugar" - 3 day streak but missed yesterday (streak broken)
        Habit h5 = new Habit(1005, "No Sugar", "\uD83C\uDF6C");
        h5.createdAt = sdf.format(new java.util.Date(now - 5 * day));
        for (int i = 1; i < 4; i++) {
            h5.completedDates.add(sdf.format(new java.util.Date(now - i * day)));
        }
        habits.add(h5);

        saveHabits(habits);
    }

    public void clearAllHabits() {
        prefs.edit().remove(KEY_HABITS).apply();
    }
}
