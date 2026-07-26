package com.hermes.habittracker;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class Habit {
    public int id;
    public String name;
    public String emoji;
    public String createdAt;
    public List<String> completedDates = new ArrayList<>();
    public int freezesUsed = 0;

    public Habit(int id, String name, String emoji) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
        this.createdAt = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
    }

    public int getStreak() {
        if (completedDates.isEmpty()) return 0;
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        String today = sdf.format(new java.util.Date());
        String yesterday = sdf.format(new java.util.Date(System.currentTimeMillis() - 86400000));

        if (!completedDates.contains(today) && !completedDates.contains(yesterday)) return 0;

        int streak = 0;
        long offset = completedDates.contains(today) ? 0 : 86400000;
        int skips = 0;
        for (int i = 0; i < 365; i++) {
            String checkDate = sdf.format(new java.util.Date(System.currentTimeMillis() - offset - (long) i * 86400000));
            if (completedDates.contains(checkDate)) {
                streak++;
            } else {
                if (skips < freezesUsed) {
                    skips++;
                } else {
                    break;
                }
            }
        }
        return streak;
    }

    public boolean isDoneToday() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        return completedDates.contains(sdf.format(new java.util.Date()));
    }

    public void toggleToday() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        String today = sdf.format(new java.util.Date());
        if (completedDates.contains(today)) {
            completedDates.remove(today);
        } else {
            completedDates.add(today);
        }
    }

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
            obj.put("freezesUsed", freezesUsed);
        } catch (Exception e) {}
        return obj.toString();
    }

    public static Habit fromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            Habit h = new Habit(obj.getInt("id"), obj.getString("name"), obj.optString("emoji", "\u2705"));
            h.createdAt = obj.optString("createdAt", null);
            JSONArray dates = obj.getJSONArray("dates");
            // If no createdAt stored, use the earliest completed date as fallback
            if (h.createdAt == null || h.createdAt.isEmpty()) {
                for (int i = 0; i < dates.length(); i++) {
                    String d = dates.getString(i);
                    if (h.createdAt == null || d.compareTo(h.createdAt) < 0) {
                        h.createdAt = d;
                    }
                }
                if (h.createdAt == null) h.createdAt = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            }
            for (int i = 0; i < dates.length(); i++) h.completedDates.add(dates.getString(i));
            h.freezesUsed = obj.optInt("freezesUsed", 0);
            return h;
        } catch (Exception e) {
            return null;
        }
    }
}
