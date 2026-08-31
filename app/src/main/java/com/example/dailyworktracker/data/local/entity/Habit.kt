package com.example.dailyworktracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A recurring habit the user wants to track, e.g. "Brush teeth" or "Do sport".
 *
 * A habit only describes *what* should be done and *when it is scheduled*. Whether it was actually
 * done on a given day lives in [HabitCompletion], which keeps the full history available for
 * streaks instead of collapsing it into a single boolean.
 */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    /** Emoji used as the habit's icon, e.g. "🪥". Keeps v1 free of an icon-asset pipeline. */
    val emoji: String,
    /** Optional accent color as "#RRGGBB"; null means "use the theme default". */
    val colorHex: String? = null,
    /**
     * Days this habit is scheduled on, as a bitmask where bit 0 is Monday and bit 6 is Sunday.
     * All seven bits set means "every day". See `WeekdaySchedule` for the helpers.
     */
    val scheduleDaysBitmask: Int,
    /** Reminder time, unused by the v1 UI but modelled so notifications can be added later. */
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    /** Creation timestamp in epoch millis; also the default list ordering. */
    val createdAt: Long,
    /** Soft delete: archived habits disappear from the UI but keep their completion history. */
    @ColumnInfo(defaultValue = "0")
    val isArchived: Boolean = false,
)
