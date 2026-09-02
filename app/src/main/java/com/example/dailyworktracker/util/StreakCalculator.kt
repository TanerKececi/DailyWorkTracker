package com.example.dailyworktracker.util

import java.time.LocalDate

/**
 * Counts how many scheduled days in a row a habit has been completed.
 *
 * Streaks are measured over a habit's *scheduled* days only: a Monday/Wednesday/Friday habit is not
 * broken by an untouched Tuesday. Days the habit was never due are skipped entirely, and so are days
 * the user deliberately skipped - a rest day taken on purpose is not a failure.
 *
 * The reference day is [asOf] rather than "today" because past days are browsable: viewing last
 * Tuesday shows the run as it stood that day.
 */
object StreakCalculator {
    /**
     * Length of the run of completed scheduled days ending at [asOf].
     *
     * If [asOf] is scheduled but not yet done the streak is not broken -- that day may still be
     * completed -- so counting simply resumes from the previous scheduled day.
     *
     * Days in [skippedDates] are passed over exactly like unscheduled days: neutral, so they neither
     * end the run nor lengthen it.
     */
    fun currentStreak(
        completedDates: Set<LocalDate>,
        scheduleDaysBitmask: Int,
        asOf: LocalDate,
        skippedDates: Set<LocalDate> = emptySet(),
    ): Int {
        if (completedDates.isEmpty() || !WeekdaySchedule.hasAnyDay(scheduleDaysBitmask)) return 0

        val earliest = completedDates.min()
        var streak = 0
        var day = asOf

        while (!day.isBefore(earliest)) {
            if (WeekdaySchedule.isScheduledOn(scheduleDaysBitmask, day.dayOfWeek) &&
                day !in skippedDates
            ) {
                when {
                    day in completedDates -> streak++
                    // The day being viewed may still be completed later, so it does not end the run.
                    day == asOf -> Unit
                    else -> return streak
                }
            }
            day = day.minusDays(1)
        }
        return streak
    }

    /**
     * The longest run of completed scheduled days achieved up to and including [asOf].
     *
     * [skippedDates] is treated the same way as in [currentStreak], so both numbers tell the same
     * story about the same history.
     */
    fun longestStreak(
        completedDates: Set<LocalDate>,
        scheduleDaysBitmask: Int,
        asOf: LocalDate,
        skippedDates: Set<LocalDate> = emptySet(),
    ): Int {
        if (completedDates.isEmpty() || !WeekdaySchedule.hasAnyDay(scheduleDaysBitmask)) return 0

        var longest = 0
        var running = 0
        var day = completedDates.min()

        while (!day.isAfter(asOf)) {
            if (WeekdaySchedule.isScheduledOn(scheduleDaysBitmask, day.dayOfWeek) &&
                day !in skippedDates
            ) {
                if (day in completedDates) {
                    running++
                    longest = maxOf(longest, running)
                } else if (day != asOf) {
                    // An unfinished current day is not yet a miss, so it must not end the run.
                    running = 0
                }
            }
            day = day.plusDays(1)
        }
        return longest
    }
}
