package com.example.dailyworktracker.data.model

import androidx.room.Embedded
import com.example.dailyworktracker.data.local.entity.Habit

/**
 * A [Habit] paired with whether it has been completed on the day being queried.
 *
 * This is the shape the habit list screen consumes: the "done" flag is derived per query date rather
 * than stored on the habit itself.
 */
data class HabitWithStatus(
    @Embedded val habit: Habit,
    val isCompleted: Boolean,
)
