package com.example.dailyworktracker.ui.habitlist

import com.example.dailyworktracker.data.model.HabitGoal
import com.example.dailyworktracker.data.model.HabitUnit

/**
 * One row of the habit list, already reduced to what the view needs to draw.
 *
 * Keeping the screen's model separate from the [com.example.dailyworktracker.data.local.entity.Habit]
 * entity means storage changes do not ripple into the UI, and DiffUtil can compare rows by value.
 *
 * The schedule stays a raw bitmask rather than a display string: turning it into localized text
 * needs a Context, which belongs to the view layer, not the ViewModel.
 */
data class HabitListItemUiModel(
    val id: Long,
    val title: String,
    val emoji: String,
    val isCompleted: Boolean,
    val scheduleDaysBitmask: Int,
    /** Consecutive scheduled days kept; 0 means there is no run worth showing yet. */
    val currentStreak: Int,
    /** How this habit is recorded: ticked off, or logged as a number. */
    val goal: HabitGoal,
    /** What was logged for the day being shown; null when nothing has been logged yet. */
    val amount: Int?,
) {
    val isAmountTracked: Boolean get() = goal is HabitGoal.Amount

    /** Null for a tick-it-off habit, which is how the layout knows to draw a checkbox instead. */
    val unit: HabitUnit? get() = (goal as? HabitGoal.Amount)?.unit
}
