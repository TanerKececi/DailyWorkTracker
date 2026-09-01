package com.example.dailyworktracker.data.model

import com.example.dailyworktracker.data.local.entity.Habit

/**
 * A habit as the today screen needs it: the habit itself, whether it is done for the day, and how
 * many scheduled days in a row it has been kept.
 *
 * Distinct from [HabitWithStatus], which is the raw Room projection. The streak is derived, so it
 * belongs to what the repository assembles rather than to what the database stores.
 */
data class TodayHabit(
    val habit: Habit,
    val isCompleted: Boolean,
    val currentStreak: Int,
    /** What was logged that day for an amount habit; null for a tick-it-off habit. */
    val amount: Int? = null,
)
