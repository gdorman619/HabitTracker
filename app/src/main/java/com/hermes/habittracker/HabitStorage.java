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
            prefs.edit().putString(KEY_FREEZE_MONTH, month).putInt("freezes_used", 0).apply();
            return 0;
        }
        return prefs.getInt("freezes_used", 0);
    }

    public boolean canUseFreeze() {
        return getFreezesUsedThisMonth() < getMaxFreezes();
    }

    public void useFreeze() {
        prefs.edit().putInt("freezes_used", getFreezesUsedThisMonth() + 1).apply();
    }

    public int getFreezesRemaining() {
        return getMaxFreezes() - getFreezesUsedThisMonth();
    }
}
