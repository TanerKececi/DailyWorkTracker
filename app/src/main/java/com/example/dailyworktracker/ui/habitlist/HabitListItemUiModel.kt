package com.example.dailyworktracker.ui.habitlist

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
)
