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
 *   - frozenDates: list of dates that were covered by a streak freeze
 *       (also used to detect whether a habit currently has an active freeze)
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
    public List<String> frozenDates = new ArrayList<>();    // dates covered by freeze (also used to detect an active freeze)

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
     *   - A missed day counts as a skip IF it was frozen (frozenDates contains it).
     *   - A second miss (or a miss with no freeze) breaks the streak.
     *
     * @return current streak in days
     */
    /**
     * Return the local yyyy-MM-dd string for "today + deltaDays", stepping by
     * Calendar day (DST-safe) instead of raw millisecond subtraction.
     */
    public static String dateOffset(int deltaDays) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        cal.add(java.util.Calendar.DATE, deltaDays);
        return sdf.format(cal.getTime());
    }

    /**
     * Calculate the current streak (consecutive covered days, with freeze tolerance).
     *
     * Walks backwards day-by-day using Calendar (DST-safe), starting at today (or
     * yesterday if today isn't covered). A "covered" day is completed OR frozen.
     * A missed day with no freeze breaks the streak.
     *
     * @return current streak in days
     */
    public int getStreak() {
        String today = dateOffset(0);
        String yesterday = dateOffset(-1);

        // A day "covers" the streak if it was completed OR frozen.
        boolean todayCovered = completedDates.contains(today) || frozenDates.contains(today);
        boolean yestCovered = completedDates.contains(yesterday) || frozenDates.contains(yesterday);

        // If neither today nor yesterday is covered, the streak is dead.
        if (!todayCovered && !yestCovered) return 0;

        int streak = 0;
        // Start the walk at today if today is covered, otherwise at yesterday.
        java.util.Calendar walk = java.util.Calendar.getInstance();
        walk.set(java.util.Calendar.HOUR_OF_DAY, 0);
        walk.set(java.util.Calendar.MINUTE, 0);
        walk.set(java.util.Calendar.SECOND, 0);
        walk.set(java.util.Calendar.MILLISECOND, 0);
        if (!todayCovered) walk.add(java.util.Calendar.DATE, -1); // start at yesterday

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        // Cap the walk (~10 years) so a pathological data set can never loop forever.
        for (int i = 0; i < 3660; i++) {
            String checkDate = sdf.format(walk.getTime());
            if (completedDates.contains(checkDate) || frozenDates.contains(checkDate)) {
                streak++; // completed OR frozen (a freeze protects the adjacent gap)
            } else {
                break;     // missed day with no freeze — streak breaks
            }
            walk.add(java.util.Calendar.DATE, -1);
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

            return h;
        } catch (Exception e) {
            return null;
        }
    }
}
