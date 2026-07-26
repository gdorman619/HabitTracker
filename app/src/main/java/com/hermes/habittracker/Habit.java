package com.hermes.habittracker;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class Habit {
    public int id;
    public String name;
    public String emoji;
    public List<String> completedDates = new ArrayList<>();

    public Habit(int id, String name, String emoji) {
        this.id = id;
        this.name = name;
        this.emoji = emoji;
    }

    public int getStreak() {
        if (completedDates.isEmpty()) return 0;
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        String today = sdf.format(new java.util.Date());
        String yesterday = sdf.format(new java.util.Date(System.currentTimeMillis() - 86400000));

        // If today is done, count from today backwards
        // If yesterday is done but not today, streak is still alive
        if (!completedDates.contains(today) && !completedDates.contains(yesterday)) return 0;

        int streak = 0;
        long offset = completedDates.contains(today) ? 0 : 86400000;
        for (int i = 0; i < 365; i++) {
            String checkDate = sdf.format(new java.util.Date(System.currentTimeMillis() - offset - (long) i * 86400000));
            if (completedDates.contains(checkDate)) {
                streak++;
            } else {
                break;
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
            JSONArray dates = new JSONArray();
            for (String d : completedDates) dates.put(d);
            obj.put("dates", dates);
        } catch (Exception e) {}
        return obj.toString();
    }

    public static Habit fromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            Habit h = new Habit(obj.getInt("id"), obj.getString("name"), obj.optString("emoji", "\u2705"));
            JSONArray dates = obj.getJSONArray("dates");
            for (int i = 0; i < dates.length(); i++) h.completedDates.add(dates.getString(i));
            return h;
        } catch (Exception e) {
            return null;
        }
    }
}
