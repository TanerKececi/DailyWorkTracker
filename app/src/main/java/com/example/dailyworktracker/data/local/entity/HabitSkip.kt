package com.example.dailyworktracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per day a [Habit] was deliberately skipped.
 *
 * Skips are their own table rather than a flag on [HabitCompletion] because a completion row
 * existing is exactly what "done" means, everywhere in the app. Folding a third outcome into that
 * table would make every query that counts completions wrong until it remembered to exclude skips.
 *
 * A skipped day is treated as a day the habit was not scheduled: it neither breaks a streak nor
 * counts against the completion rate. Skipped and completed are mutually exclusive - the repository
 * clears one when it writes the other.
 */
@Entity(
    tableName = "habit_skips",
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
data class HabitSkip(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitId: Long,
    /** The day being skipped, as `LocalDate.toEpochDay()`. */
    val date: Long,
    /** When the user swiped it away, in epoch millis. */
    val createdAt: Long,
)
