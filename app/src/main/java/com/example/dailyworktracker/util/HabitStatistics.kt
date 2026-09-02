package com.example.dailyworktracker.util

import java.time.LocalDate

/**
 * Aggregate numbers over a habit's history.
 *
 * Like [StreakCalculator], only *scheduled* days count: a Mon/Wed/Fri habit is never judged on a
 * Tuesday, and a day the user deliberately skipped is judged no more harshly. Pure and
 * dependency-free, so the arithmetic can be tested without Room or a clock.
 */
object HabitStatistics {
    /**
     * How many scheduled days fall in `[from, to]`, ignoring an unfinished final day.
     *
     * The last day only counts once it has resolved. Otherwise opening the app each morning would
     * show the rate dip, purely because today has not happened yet — the same reasoning that stops
     * an unfinished today breaking a streak.
     *
     * Days in [skippedDates] leave the count for the same reason unscheduled days never entered it:
     * the habit was not owed that day. A skip counted as a miss would say nothing that not ticking
     * does not already say.
     */
    fun scheduledDayCount(
        completedDates: Set<LocalDate>,
        scheduleDaysBitmask: Int,
        from: LocalDate,
        to: LocalDate,
        skippedDates: Set<LocalDate> = emptySet(),
    ): Int {
        if (from.isAfter(to) || !WeekdaySchedule.hasAnyDay(scheduleDaysBitmask)) return 0

        var count = 0
        var day = from
        while (!day.isAfter(to)) {
            if (WeekdaySchedule.isScheduledOn(scheduleDaysBitmask, day.dayOfWeek) &&
                day !in skippedDates &&
                !(day == to && day !in completedDates)
            ) {
                count++
            }
            day = day.plusDays(1)
        }
        return count
    }

    /**
     * Completions that landed on a scheduled day inside `[from, to]`.
     *
     * A skipped day is excluded here too, so the numerator and the denominator are always counted
     * over the same set of days and the rate cannot exceed 100%.
     */
    fun completedDayCount(
        completedDates: Set<LocalDate>,
        scheduleDaysBitmask: Int,
        from: LocalDate,
        to: LocalDate,
        skippedDates: Set<LocalDate> = emptySet(),
    ): Int =
        completedDates.count { date ->
            !date.isBefore(from) &&
                !date.isAfter(to) &&
                date !in skippedDates &&
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
        skippedDates: Set<LocalDate> = emptySet(),
    ): Float {
        val scheduled = scheduledDayCount(completedDates, scheduleDaysBitmask, from, to, skippedDates)
        if (scheduled == 0) return 0f
        val completed = completedDayCount(completedDates, scheduleDaysBitmask, from, to, skippedDates)
        return completed.toFloat() / scheduled
    }
}
