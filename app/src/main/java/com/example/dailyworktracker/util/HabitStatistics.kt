package com.example.dailyworktracker.util

import java.time.LocalDate

/**
 * Aggregate numbers over a habit's history.
 *
 * Like [StreakCalculator], only *scheduled* days count: a Mon/Wed/Fri habit is never judged on a
 * Tuesday. Pure and dependency-free, so the arithmetic can be tested without Room or a clock.
 */
object HabitStatistics {
    /**
     * How many scheduled days fall in `[from, to]`, ignoring an unfinished final day.
     *
     * The last day only counts once it has resolved. Otherwise opening the app each morning would
     * show the rate dip, purely because today has not happened yet — the same reasoning that stops
     * an unfinished today breaking a streak.
     */
    fun scheduledDayCount(
        completedDates: Set<LocalDate>,
        scheduleDaysBitmask: Int,
        from: LocalDate,
        to: LocalDate,
    ): Int {
        if (from.isAfter(to) || !WeekdaySchedule.hasAnyDay(scheduleDaysBitmask)) return 0

        var count = 0
        var day = from
        while (!day.isAfter(to)) {
            if (WeekdaySchedule.isScheduledOn(scheduleDaysBitmask, day.dayOfWeek) &&
                !(day == to && day !in completedDates)
            ) {
                count++
            }
            day = day.plusDays(1)
        }
        return count
    }

    /** Completions that landed on a scheduled day inside `[from, to]`. */
    fun completedDayCount(
        completedDates: Set<LocalDate>,
        scheduleDaysBitmask: Int,
        from: LocalDate,
        to: LocalDate,
    ): Int =
        completedDates.count { date ->
            !date.isBefore(from) &&
                !date.isAfter(to) &&
                WeekdaySchedule.isScheduledOn(scheduleDaysBitmask, date.dayOfWeek)
        }

    /**
     * Share of scheduled days that were completed, in `0f..1f`.
     *
     * Returns 0 when nothing has been due yet, which reads better than an undefined rate.
     */
    fun completionRate(
        completedDates: Set<LocalDate>,
        scheduleDaysBitmask: Int,
        from: LocalDate,
        to: LocalDate,
    ): Float {
        val scheduled = scheduledDayCount(completedDates, scheduleDaysBitmask, from, to)
        if (scheduled == 0) return 0f
        val completed = completedDayCount(completedDates, scheduleDaysBitmask, from, to)
        return completed.toFloat() / scheduled
    }
}
