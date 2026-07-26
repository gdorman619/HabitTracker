package com.hermes.habittracker;

/*
 * Habit.java — Data model for a single habit.
 *
 * Each habit stores:
 *   - id: unique identifier (based on timestamp)
 *   - name: user-visible label (e.g. "Drink Water")
 *   - emoji: emoji shown in list/widget (e.g. 💧)
 *   - createdAt: date the habit was created (yyyy-MM-dd). Used by the calendar
 *       to distinguish "before habit existed" (neutral N/A) from "missed" days.
 *   - completedDates: list of dates (yyyy-MM-dd) the habit was marked done.
 *   - frozenDates: list of dates that were covered by a streak freeze.
 *   - freezesUsed: how many freezes have been applied to this habit (0 or 1).
 *
 * Serialized to/from JSON for SharedPreferences storage. No Room/SQLite —
 * keeps the app simple and 100% offline.
 */

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Habit {
    public int id;
    public String name;
    public String emoji;
    public String createdAt;                    // yyyy-MM-dd, set on creation
    public List<String> completedDates = new ArrayList<>();  // dates marked done
    public List<String> frozenDates = new ArrayList<>();    // dates covered by freeze
    public int freezesUsed = 0;                 // 0 = not frozen, 1 = frozen

    /** Create a new habit. createdAt defaults to today. */
    public Habit(int id, String name, String emoji) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
        this.createdAt = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
    }

    /**
     * Calculate the current streak (consecutive days completed, with freeze tolerance).
     *
     * Logic:
     *   - If neither today nor yesterday is completed, streak = 0.
     *   - Otherwise, walk backwards day-by-day from today (or yesterday).
     *   - Each completed day increments the streak.
     *   - A missed day counts as a skip IF freezesUsed > 0 (freeze covers it).
     *   - A second miss (or a miss with no freeze) breaks the streak.
     *
     * @return current streak in days
     */
    public int getStreak() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String today = sdf.format(new java.util.Date());
        String yesterday = sdf.format(new java.util.Date(System.currentTimeMillis() - 86400000L));

        // A day "covers" the streak if it was completed OR frozen.
        boolean todayCovered = completedDates.contains(today) || frozenDates.contains(today);
        boolean yestCovered = completedDates.contains(yesterday) || frozenDates.contains(yesterday);

        // If neither today nor yesterday is covered, the streak is dead.
        if (!todayCovered && !yestCovered) return 0;

        int streak = 0;
        // Start the walk at today if today is covered (done or frozen), otherwise
        // at yesterday — the streak is still alive via yesterday.
        long offset = (todayCovered) ? 0 : 86400000L;

        for (int i = 0; i < 366; i++) {
            String checkDate = sdf.format(new java.util.Date(System.currentTimeMillis() - offset - (long) i * 86400000L));
            if (completedDates.contains(checkDate)) {
                streak++;
            } else if (frozenDates.contains(checkDate)) {
                // This missed day is covered by a freeze — counted, streak continues.
                // A freeze can only be applied to today/yesterday (enforced at apply
                // time in HabitStorage), so a frozen date here is always an adjacent
                // gap that legitimately protects the streak.
                streak++;
            } else {
                // Missed day with no freeze — streak breaks.
                break;
            }
        }
        return streak;
    }

    /**
     * Calculate the longest streak (consecutive covered days) this habit has ever had.
     *
     * A "covered" day is one that was completed OR frozen. We walk from the
     * creation date through today and track the longest run of consecutive
     * covered days. Frozen days extend a run just like completed days, so a
     * best streak reflects freezes that were used to protect it.
     *
     * @return best (longest) streak in days, 0 if no days were ever covered
     */
    public int getBestStreak() {
        if (completedDates.isEmpty() && frozenDates.isEmpty()) return 0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        java.util.Set<String> covered = new java.util.HashSet<>();
        covered.addAll(completedDates);
        covered.addAll(frozenDates);

        // Start walking at the creation date (fall back to earliest completed date).
        java.util.Calendar cal = java.util.Calendar.getInstance();
        if (createdAt != null && !createdAt.isEmpty()) {
            try { cal.setTime(sdf.parse(createdAt)); } catch (Exception ignored) {}
        } else if (!completedDates.isEmpty()) {
            String earliest = completedDates.get(0);
            for (String d : completedDates) if (d.compareTo(earliest) < 0) earliest = d;
            try { cal.setTime(sdf.parse(earliest)); } catch (Exception ignored) {}
        }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);

        java.util.Calendar today = java.util.Calendar.getInstance();
        today.set(java.util.Calendar.HOUR_OF_DAY, 0);
        today.set(java.util.Calendar.MINUTE, 0);
        today.set(java.util.Calendar.SECOND, 0);
        today.set(java.util.Calendar.MILLISECOND, 0);

        int best = 0, run = 0;
        while (!cal.after(today)) {
            if (covered.contains(sdf.format(cal.getTime()))) {
                run++;
                if (run > best) best = run;
            } else {
                run = 0;
            }
            cal.add(java.util.Calendar.DATE, 1);
        }
        return best;
    }

    /** Check if the habit was completed today. */
    public boolean isDoneToday() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return completedDates.contains(sdf.format(new java.util.Date()));
    }

    /** Toggle today's completion: if done, un-do it; if not done, mark it done. */
    public void toggleToday() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String today = sdf.format(new java.util.Date());
        if (completedDates.contains(today)) {
            completedDates.remove(today);
        } else {
            completedDates.add(today);
        }
    }

    /** Serialize this habit to a JSON string for SharedPreferences storage. */
    public String toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("name", name);
            obj.put("emoji", emoji);
            obj.put("createdAt", createdAt);
            JSONArray dates = new JSONArray();
            for (String d : completedDates) dates.put(d);
            obj.put("dates", dates);
            JSONArray frozen = new JSONArray();
            for (String d : frozenDates) frozen.put(d);
            obj.put("frozenDates", frozen);
            obj.put("freezesUsed", freezesUsed);
        } catch (Exception e) {}
        return obj.toString();
    }

    /**
     * Deserialize a habit from a JSON string.
     *
     * Handles backward compatibility: if createdAt is missing (old data),
     * falls back to the earliest completed date. If frozenDates is missing,
     * defaults to an empty list.
     *
     * @param json JSON string from SharedPreferences
     * @return Habit object, or null if parsing fails
     */
    public static Habit fromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            Habit h = new Habit(obj.getInt("id"), obj.getString("name"), obj.optString("emoji", "\u2705"));
            h.createdAt = obj.optString("createdAt", null);
            JSONArray dates = obj.getJSONArray("dates");

            // Backward compat: if no createdAt stored, use earliest completion as fallback
            if (h.createdAt == null || h.createdAt.isEmpty()) {
                for (int i = 0; i < dates.length(); i++) {
                    String d = dates.getString(i);
                    if (h.createdAt == null || d.compareTo(h.createdAt) < 0) {
                        h.createdAt = d;
                    }
                }
                if (h.createdAt == null) {
                    h.createdAt = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
                }
            }

            // Load completed dates
            for (int i = 0; i < dates.length(); i++) h.completedDates.add(dates.getString(i));

            // Load frozen dates (backward compat: defaults to empty)
            JSONArray frozen = obj.optJSONArray("frozenDates");
            if (frozen != null) {
                for (int i = 0; i < frozen.length(); i++) h.frozenDates.add(frozen.getString(i));
            }

            h.freezesUsed = obj.optInt("freezesUsed", 0);
            return h;
        } catch (Exception e) {
            return null;
        }
    }
}
