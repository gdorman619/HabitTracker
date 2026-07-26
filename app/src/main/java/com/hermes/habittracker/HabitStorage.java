package com.hermes.habittracker;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class HabitStorage {
    private static final String PREFS = "habit_storage";
    private static final String KEY_HABITS = "habits_json";
    private static final String KEY_UNLOCKED = "is_unlocked";
    private static final int FREE_LIMIT = 5;

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
}
