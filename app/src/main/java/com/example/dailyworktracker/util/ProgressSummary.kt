package com.example.dailyworktracker.util

import com.example.dailyworktracker.ui.common.DayStatus
import java.time.LocalDate
import java.time.YearMonth

/**
 * One habit's month, reduced to what the arithmetic needs.
 *
 * Carries dates rather than rows: like [StreakCalculator], nothing here cares how much was logged,
 * only which days have a record.
 */
data class HabitMonth(
    val habitId: Long,
    val scheduleDaysBitmask: Int,
    val createdOn: LocalDate,
    val completed: Set<LocalDate>,
    val skipped: Set<LocalDate>,
)

/**
 * The Progress screen's numbers, for a whole month across every habit.
 *
 * Deliberately built *on* [HabitStatistics] rather than beside it: one habit's rate is already
 * defined there, and re-deriving it here would let the two disagree about what a skipped day or an
 * unresolved final day means. This object only decides how several habits combine.
 */
object ProgressSummary {
    /**
     * Share of everything due in [month] that was done, in `0f..1f`.
     *
     * Weighted by how often each habit was due: numerator and denominator are summed across habits
     * before dividing once. A habit due daily therefore counts for more than one due on Mondays,
     * which is what a single headline percentage has to mean - averaging per-habit rates would let
     * a weekly habit that was kept hide a daily one missed all month.
     */
    fun completionRate(
        habits: List<HabitMonth>,
        month: YearMonth,
        today: LocalDate,
    ): Float {
        var scheduled = 0
        var completed = 0

        habits.forEach { habit ->
            val from = maxOf(month.atDay(1), habit.createdOn)
            // The month runs no further than today: days that have not happened are not misses.
            val to = minOf(month.atEndOfMonth(), today)
            if (from.isAfter(to)) return@forEach

            scheduled +=
                HabitStatistics.scheduledDayCount(
                    habit.completed,
                    habit.scheduleDaysBitmask,
                    from,
                    to,
                    habit.skipped,
                )
            completed +=
                HabitStatistics.completedDayCount(
                    habit.completed,
                    habit.scheduleDaysBitmask,
                    from,
                    to,
                    habit.skipped,
                )
        }

        // Nothing due yet reads better as 0 than as an undefined rate, matching HabitStatistics.
        return if (scheduled == 0) 0f else completed.toFloat() / scheduled
    }

    /** One habit's own rate for [month], for the per-habit breakdown. */
    fun rateFor(
        habit: HabitMonth,
        month: YearMonth,
        today: LocalDate,
    ): Float = completionRate(listOf(habit), month, today)

    /**
     * Share of that day's due habits that were done, in `0f..1f`.
     *
     * The calendar draws a day as a ring rather than a filled box, so it needs the proportion and
     * not only the state. Judged over the same set of habits [dayStatus] uses: one that was not due,
     * did not exist yet, or was skipped is owed nothing and counts neither way.
     *
     * A day with nothing owed reports 0, not 1. Zero of zero drawn as a full ring would claim
     * credit for a day off.
     */
    fun dayFraction(
        habits: List<HabitMonth>,
        date: LocalDate,
    ): Float {
        val due = habits.filter { it.owesSomethingOn(date) }
        if (due.isEmpty()) return 0f
        return due.count { date in it.completed }.toFloat() / due.size
    }

    /**
     * How one calendar cell should be drawn, given every habit that was due that day.
     *
     * A day is judged only on the habits that actually owed something: one that does not repeat
     * that weekday, did not exist yet, or was deliberately skipped is left out of the count
     * entirely, exactly as it is left out of the rate.
     */
    fun dayStatus(
        habits: List<HabitMonth>,
        date: LocalDate,
        today: LocalDate,
    ): DayStatus {
        if (date.isAfter(today)) return DayStatus.OUT_OF_RANGE

        val due = habits.filter { it.owesSomethingOn(date) }

        if (due.isEmpty()) {
            // Nothing was owed. Skipped only where a habit was genuinely due and passed on, so a
            // deliberate rest day reads differently from a weekday the schedule never touches.
            val wasSkipped =
                habits.any { habit ->
                    date in habit.skipped &&
                        !date.isBefore(habit.createdOn) &&
                        WeekdaySchedule.isScheduledOn(habit.scheduleDaysBitmask, date.dayOfWeek)
                }
            return if (wasSkipped) DayStatus.SKIPPED else DayStatus.NOT_SCHEDULED
        }

        val done = due.count { date in it.completed }
        return when {
            done == due.size -> DayStatus.COMPLETED
            done > 0 -> DayStatus.PARTIAL
            // Today has not resolved yet, so an untouched today is not a miss.
            date == today -> DayStatus.PENDING
            else -> DayStatus.MISSED
        }
    }

    /**
     * Whether this habit actually owed something on [date].
     *
     * The single definition of "due", so the ring and the status can never disagree about which
     * habits a day is being judged on.
     */
    private fun HabitMonth.owesSomethingOn(date: LocalDate): Boolean =
        !date.isBefore(createdOn) &&
            WeekdaySchedule.isScheduledOn(scheduleDaysBitmask, date.dayOfWeek) &&
            date !in skipped
}
