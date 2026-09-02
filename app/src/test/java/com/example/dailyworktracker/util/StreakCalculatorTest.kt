package com.example.dailyworktracker.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StreakCalculatorTest {
    private val monday = LocalDate.of(2026, 8, 31)

    private fun daysBefore(vararg offsets: Long) = offsets.map(monday::minusDays).toSet()

    @Test
    fun `no completions means no streak`() {
        assertEquals(
            0,
            StreakCalculator.currentStreak(emptySet(), WeekdaySchedule.EVERY_DAY, monday),
        )
    }

    @Test
    fun `an unscheduled habit has no streak`() {
        // Guards the loop bound: with no scheduled days there is no previous scheduled day to find.
        assertEquals(
            0,
            StreakCalculator.currentStreak(daysBefore(0), WeekdaySchedule.NONE, monday),
        )
    }

    @Test
    fun `counts consecutive completed days up to today`() {
        assertEquals(
            3,
            StreakCalculator.currentStreak(daysBefore(0, 1, 2), WeekdaySchedule.EVERY_DAY, monday),
        )
    }

    @Test
    fun `an incomplete today does not break the streak`() {
        // The day is still in progress, so yesterday's run must still count.
        assertEquals(
            2,
            StreakCalculator.currentStreak(daysBefore(1, 2), WeekdaySchedule.EVERY_DAY, monday),
        )
    }

    @Test
    fun `a missed day ends the streak`() {
        // Completed today and yesterday, missed the day before, then two older ones.
        assertEquals(
            2,
            StreakCalculator.currentStreak(
                daysBefore(0, 1, 3, 4),
                WeekdaySchedule.EVERY_DAY,
                monday,
            ),
        )
    }

    @Test
    fun `days the habit is not scheduled on do not break the streak`() {
        // Mon/Wed/Fri habit completed on its three scheduled days; the gaps are not misses.
        val schedule =
            WeekdaySchedule.toBitmask(
                listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            )
        // monday, then the previous Friday and the Wednesday before it.
        val completed = setOf(monday, monday.minusDays(3), monday.minusDays(5))

        assertEquals(3, StreakCalculator.currentStreak(completed, schedule, monday))
    }

    @Test
    fun `missing a scheduled day breaks a partial week schedule`() {
        val schedule =
            WeekdaySchedule.toBitmask(
                listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            )
        // Today and last Friday done, but the Wednesday before that was skipped.
        val completed = setOf(monday, monday.minusDays(3), monday.minusDays(7))

        assertEquals(2, StreakCalculator.currentStreak(completed, schedule, monday))
    }

    @Test
    fun `completions on unscheduled days are ignored`() {
        val schedule = WeekdaySchedule.toBitmask(listOf(DayOfWeek.MONDAY))
        val completed = setOf(monday, monday.minusDays(1), monday.minusDays(2))

        // Only the Mondays count, so the Sunday and Saturday entries add nothing.
        assertEquals(1, StreakCalculator.currentStreak(completed, schedule, monday))
    }

    @Test
    fun `longest streak finds the best past run`() {
        // A four-day run, a miss, then a two-day run ending today.
        val completed = daysBefore(0, 1, 3, 4, 5, 6)

        assertEquals(
            4,
            StreakCalculator.longestStreak(completed, WeekdaySchedule.EVERY_DAY, monday),
        )
    }

    @Test
    fun `longest streak equals current streak when there is one unbroken run`() {
        val completed = daysBefore(0, 1, 2)

        assertEquals(
            StreakCalculator.currentStreak(completed, WeekdaySchedule.EVERY_DAY, monday),
            StreakCalculator.longestStreak(completed, WeekdaySchedule.EVERY_DAY, monday),
        )
    }

    @Test
    fun `longest streak is zero without completions`() {
        assertEquals(
            0,
            StreakCalculator.longestStreak(emptySet(), WeekdaySchedule.EVERY_DAY, monday),
        )
    }

    @Test
    fun `a skipped day does not break the streak`() {
        // The whole point of the feature: a deliberate rest day is not a failure. Without this the
        // user is better off never marking anything, which is what not ticking already does.
        val completed = daysBefore(0, 2)

        assertEquals(
            2,
            StreakCalculator.currentStreak(
                completed,
                WeekdaySchedule.EVERY_DAY,
                monday,
                skippedDates = daysBefore(1),
            ),
        )
    }

    @Test
    fun `a skipped day does not itself count towards the streak`() {
        // Neutral means neutral: it is passed over like an unscheduled day, not credited as done.
        assertEquals(
            1,
            StreakCalculator.currentStreak(
                daysBefore(0),
                WeekdaySchedule.EVERY_DAY,
                monday,
                skippedDates = daysBefore(1, 2, 3),
            ),
        )
    }

    @Test
    fun `a skipped day does not break the longest streak either`() {
        // The two calculators have to agree, or the detail screen contradicts itself.
        val completed = daysBefore(1, 3)

        assertEquals(
            2,
            StreakCalculator.longestStreak(
                completed,
                WeekdaySchedule.EVERY_DAY,
                monday,
                skippedDates = daysBefore(2),
            ),
        )
    }
}
