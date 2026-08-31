package com.example.dailyworktracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per day a [Habit] was actually completed.
 *
 * Storing completions as their own table (rather than a `isDoneToday` flag on [Habit]) is what makes
 * history and streaks possible: the absence of a row for a date means "not done that day".
 */
@Entity(
    tableName = "habit_completions",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["habitId", "date"], unique = true)],
)
data class HabitCompletion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitId: Long,
    /** The day this completion belongs to, as `LocalDate.toEpochDay()`. */
    val date: Long,
    /** When the user actually ticked it off, in epoch millis. */
    val completedAt: Long,
)
