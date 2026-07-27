package com.hermes.habittracker;

/*
 * HabitAdapter.java — RecyclerView adapter for the main habit list.
 *
 * Each row shows: emoji, name, streak count (🔥 N day streak), freeze badge (❄️),
 * and a checkbox to toggle today's completion.
 *
 * Interactions:
 *   - Tap checkbox → onToggle() (marks done/not-done for today)
 *   - Tap row      → onClick() (opens detail screen)
 *   - Long-press   → onLongClick() (shows edit/delete/history menu)
 */

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class HabitAdapter extends RecyclerView.Adapter<HabitAdapter.VH> {
    private List<Habit> habits;
    private final OnHabitToggle listener;
    private final HabitStorage storage;

    public interface OnHabitToggle {
        void onToggle(int position);    // checkbox tapped
        void onLongClick(int position); // long-press (show menu)
        void onClick(int position);     // tap row (open detail)
    }

    public HabitAdapter(List<Habit> habits, OnHabitToggle listener, HabitStorage storage) {
        this.habits = habits;
        this.listener = listener;
        this.storage = storage;
    }

    public void update(List<Habit> newHabits) {
        this.habits = newHabits;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_habit, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Habit h = habits.get(position);
        holder.emoji.setText(h.emoji);
        holder.name.setText(h.name);

        // Show freeze badge if the habit has a freeze active this month
        if (storage.hasFrozenThisMonth(h)) {
            holder.freezeBadge.setVisibility(View.VISIBLE);
        } else {
            holder.freezeBadge.setVisibility(View.GONE);
        }

        // A streak is only shown as "live" when today is actually done. If today is
        // unchecked, the streak is 0 (broken) — the checkbox and streak status must
        // agree. (getStreak()'s calendar semantics still apply for the detail/calendar
        // view, where yesterday-completed is a legit in-progress streak.)
        int streak = h.isDoneToday() ? h.getStreak() : 0;
        if (streak > 0) {
            holder.streak.setText("\uD83D\uDD25 " + streak + " day streak");
            holder.streak.setVisibility(View.VISIBLE);
        } else {
            holder.streak.setVisibility(View.GONE);
        }
        holder.check.setOnCheckedChangeListener((v, checked) -> {
            // Use the binding adapter position (stable during layout); ignore if the
            // row is mid-recycle (position == NO_POSITION) to avoid ArrayIndexOutOfBounds.
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) listener.onToggle(pos);
        });
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) listener.onClick(pos);
        });
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) listener.onLongClick(pos);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return habits.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView emoji, name, streak, freezeBadge;
        CheckBox check;
        VH(View v) {
            super(v);
            emoji = v.findViewById(R.id.habitEmoji);
            name = v.findViewById(R.id.habitName);
            streak = v.findViewById(R.id.habitStreak);
            freezeBadge = v.findViewById(R.id.freezeBadge);
            check = v.findViewById(R.id.habitCheck);
        }
    }
}
