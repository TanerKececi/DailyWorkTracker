package com.example.dailyworktracker.util

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Works out when a habit's reminder should next fire.
 *
 * Pure and clock-free — "now" arrives as a parameter — so the cases that are awkward to observe in a
 * running app (the time has already passed today, today is not a scheduled day, only one weekday is
 * scheduled) are ordinary unit tests instead of something you can only check by waiting.
 */
object ReminderSchedule {
    /**
     * The first moment strictly after [after] when a habit repeating on [scheduleDaysBitmask] should
     * be reminded at [reminderTime], or null if it repeats on no days at all.
     *
     * Strictly after, so a worker that wakes at exactly the reminder time and asks for the next one
     * is told about tomorrow rather than the occurrence it is already handling.
     */
    fun nextOccurrence(
        scheduleDaysBitmask: Int,
        reminderTime: LocalTime,
        after: LocalDateTime,
    ): LocalDateTime? {
        if (!WeekdaySchedule.hasAnyDay(scheduleDaysBitmask)) return null

        return generateSequence(after.toLocalDate()) { it.plusDays(1) }
            .take(DAYS_TO_SEARCH)
            .filter { WeekdaySchedule.isScheduledOn(scheduleDaysBitmask, it.dayOfWeek) }
            .map { it.atTime(reminderTime) }
            .firstOrNull { it.isAfter(after) }
    }

    /**
     * Today plus a full week. A habit on a single weekday whose time has already passed fires again
     * seven days later, which is the furthest away any occurrence can be.
     */
    private const val DAYS_TO_SEARCH = 8
}
