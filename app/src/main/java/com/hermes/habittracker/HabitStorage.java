package com.hermes.habittracker;

/*
 * HabitStorage.java — Persistence layer for habits.
 *
 * Uses SharedPreferences + JSON serialization (no SQLite/Room). All data stays
 * on-device, 100% offline, no account needed.
 *
 * Responsibilities:
 *   - CRUD: add, delete, update, load habits
 *   - Free tier limit: max 5 habits for free users, unlimited for paid
 *   - Streak freeze management: global monthly limit, per-habit freeze application
 *   - Test data injection: generates 5 sample habits with varied streaks
 */

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HabitStorage {
    private static final String PREFS = "habit_storage";
    private static final String KEY_HABITS = "habits_json";        // JSON array of all habits
    private static final String KEY_UNLOCKED = "is_unlocked";     // true = paid $2.99
    private static final String KEY_FREEZE_MONTH = "freeze_month"; // "yyyy-MM" for monthly reset
    private static final String KEY_FREEZES_USED = "freezes_used_global"; // global monthly counter
    private static final int FREE_LIMIT = 5;                     // max habits for free users
    private static final int FREE_FREEZES_PER_MONTH = 1;        // free users: 1 freeze/month
    private static final int PAID_FREEZES_PER_MONTH = 3;        // paid users: 3 freezes/month

    private final SharedPreferences prefs;

    public HabitStorage(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // === CRUD operations ===

    /** Load all habits from storage, parsed from JSON. Returns empty list if none. */
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
        // Generate a unique id: base on the clock, then walk forward until it
        // doesn't collide with any existing habit (two habits created in the same
        // millisecond used to get the same id and silently overwrite each other).
        int id = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
        List<Habit> existing = loadHabits();
        while (true) {
            boolean dup = false;
            for (Habit h : existing) {
                if (h.id == id) { dup = true; break; }
            }
            if (!dup) break;
            id = (id + 1) % Integer.MAX_VALUE;
        }
        return id;
    }

    // === Freeze logic ===
    // Global monthly limit: total freezes across ALL habits per month
    // Per-habit: a habit is "frozen" if frozenDates is non-empty; eligibility
    // re-evaluates each month as the quota resets.

    /** Maximum freezes available per month based on free/paid status. */
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

    /** True if the habit has used a freeze in the CURRENT month. Old freezes (prior
     *  months) no longer block a new freeze — they've already done their job
     *  protecting a past gap and are just history. This is what lets a habit be
     *  re-frozen each month instead of being locked after the first use. */
    public boolean hasFrozenThisMonth(Habit h) {
        String thisMonth = getCurrentMonth();
        for (String d : h.frozenDates) {
            if (d.length() >= 7 && d.substring(0, 7).equals(thisMonth)) return true;
        }
        return false;
    }

    /** Check if a specific habit can be frozen: not already frozen THIS MONTH AND global limit not reached. */
    public boolean canFreezeHabit(Habit h) {
        return !hasFrozenThisMonth(h) && canUseFreeze();
    }

    /**
     * Apply a freeze to a specific habit.
     * Finds the most recent missed day (between createdAt and today) and marks it frozen.
     * Increments the global monthly freeze counter.
     *
     * @return true if freeze was applied, false if not allowed
     */
    public boolean freezeHabit(Habit h) {
        if (!canFreezeHabit(h)) return false;
        // A freeze only protects the adjacent gap: today, or yesterday. Any older
        // missed day is already a real break in the streak and can't be revived.
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String today = sdf.format(new java.util.Date());
        String yesterday = sdf.format(new java.util.Date(System.currentTimeMillis() - 86400000L));
        String target = null;
        if (!h.completedDates.contains(today) && !h.frozenDates.contains(today)) {
            target = today;          // today was missed -> freeze it
        } else if (!h.completedDates.contains(yesterday) && !h.frozenDates.contains(yesterday)) {
            target = yesterday;      // yesterday was missed -> freeze it
        }
        if (target == null) {
            // No adjacent missed day to protect — nothing to freeze.
            return false;
        }
        // A freeze can only protect a day strictly AFTER the habit was created —
        // there's no prior streak to preserve on the creation day itself, and
        // freezing a day before creation is impossible. Result: a habit created
        // today offers no freeze; a habit created yesterday can still freeze
        // yesterday or today as normal.
        if (h.createdAt != null && target.compareTo(h.createdAt) <= 0) {
            return false;
        }
        h.frozenDates.add(target);
        updateHabit(h);
        useFreeze();
        return true;
    }

    // === Test data injection ===
    // Generates 5 sample habits with varied streak patterns for testing/preview.
    // Replaces all existing habits. Triggered by long-pressing the + button.

    /** Inject 5 test habits with different streaks, misses, and a freeze. */
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
        h3.frozenDates.add(sdf.format(new java.util.Date(now - 3 * day)));
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

        // Also reset the monthly freeze counter so the freeze UI isn't stuck on a
        // quota that test data "used up" in a previous session.
        prefs.edit().putString(KEY_FREEZE_MONTH, getCurrentMonth()).putInt(KEY_FREEZES_USED, 0).apply();

        saveHabits(habits);
    }

    public void clearAllHabits() {
        prefs.edit().remove(KEY_HABITS).apply();
    }
}
