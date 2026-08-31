package com.example.dailyworktracker.util

import com.example.dailyworktracker.data.local.entity.Habit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Decides whether a habit belongs on a given day's list.
 *
 * Two rules combine: the habit must repeat on that weekday, and it must already have existed. The
 * second rule matters now that past days are browsable — without it every habit would show a wall of
 * "missed" days stretching back before it was ever created.
 */
object HabitVisibility {
    /**
     * [zone] is a parameter rather than read inline so the epoch-millis to calendar-day conversion
     * can be tested without depending on the machine's time zone.
     */
    fun isActiveOn(
        habit: Habit,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean =
        !habit.isArchived &&
            WeekdaySchedule.isScheduledOn(habit.scheduleDaysBitmask, date.dayOfWeek) &&
            !createdDate(habit, zone).isAfter(date)

    /** The calendar day a habit was created, in [zone]. */
    fun createdDate(
        habit: Habit,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate = Instant.ofEpochMilli(habit.createdAt).atZone(zone).toLocalDate()
}
