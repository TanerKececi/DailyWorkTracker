package com.example.dailyworktracker.fake

import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.util.WeekdaySchedule

/**
 * Builds a [Habit] with sensible defaults so each test only states the fields it cares about,
 * keeping the intent of a test visible instead of buried in boilerplate.
 */
fun habit(
    id: Long = 1L,
    title: String = "Brush teeth",
    emoji: String = "🪥",
    scheduleDaysBitmask: Int = WeekdaySchedule.EVERY_DAY,
    createdAt: Long = 0L,
    isArchived: Boolean = false,
) = Habit(
    id = id,
    title = title,
    emoji = emoji,
    scheduleDaysBitmask = scheduleDaysBitmask,
    createdAt = createdAt,
    isArchived = isArchived,
)
