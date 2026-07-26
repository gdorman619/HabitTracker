package com.hermes.habittracker;

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

    public interface OnHabitToggle {
        void onToggle(int position);
        void onLongClick(int position);
        void onClick(int position);
    }

    public HabitAdapter(List<Habit> habits, OnHabitToggle listener) {
        this.habits = habits;
        this.listener = listener;
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

        // Show freeze badge if habit has a freeze applied
        if (h.freezesUsed > 0) {
            holder.freezeBadge.setVisibility(View.VISIBLE);
        } else {
            holder.freezeBadge.setVisibility(View.GONE);
        }

        int streak = h.getStreak();
        if (streak > 0) {
            holder.streak.setText("\uD83D\uDD25 " + streak + " day streak");
            holder.streak.setVisibility(View.VISIBLE);
        } else {
            holder.streak.setVisibility(View.GONE);
        }
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(h.isDoneToday());
        holder.check.setOnCheckedChangeListener((v, checked) -> {
            if (listener != null) listener.onToggle(holder.getAdapterPosition());
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(holder.getAdapterPosition());
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onLongClick(holder.getAdapterPosition());
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
