package com.example.dailyworktracker.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitStatisticsTest {
    private val monday = LocalDate.of(2026, 8, 31)

    private fun daysBefore(vararg offsets: Long) = offsets.map(monday::minusDays).toSet()

    @Test
    fun `a fully kept daily habit scores one`() {
        val completed = daysBefore(0, 1, 2, 3)

        assertEquals(
            1f,
            HabitStatistics.completionRate(
                completed,
                WeekdaySchedule.EVERY_DAY,
                from = monday.minusDays(3),
                to = monday,
            ),
            0f,
        )
    }

    @Test
    fun `missing half the days scores one half`() {
        val completed = daysBefore(0, 2)

        assertEquals(
            0.5f,
            HabitStatistics.completionRate(
                completed,
                WeekdaySchedule.EVERY_DAY,
                from = monday.minusDays(3),
                to = monday,
            ),
            0f,
        )
    }

    @Test
    fun `an unfinished final day is not counted against the rate`() {
        // Yesterday and the day before were kept; today has simply not happened yet.
        val completed = daysBefore(1, 2)

        assertEquals(
            1f,
            HabitStatistics.completionRate(
                completed,
                WeekdaySchedule.EVERY_DAY,
                from = monday.minusDays(2),
                to = monday,
            ),
            0f,
        )
    }

    @Test
    fun `days the habit is not scheduled on are ignored`() {
        // Monday-only habit: the six other days in the week must not dilute the rate.
        val schedule = WeekdaySchedule.toBitmask(listOf(DayOfWeek.MONDAY))
        val completed = setOf(monday.minusDays(7))

        assertEquals(
            1f,
            HabitStatistics.completionRate(
                completed,
                schedule,
                from = monday.minusDays(7),
                to = monday.minusDays(1),
            ),
            0f,
        )
    }

    @Test
    fun `completions outside the range do not count`() {
        val completed = daysBefore(10)

        assertEquals(
            0,
            HabitStatistics.completedDayCount(
                completed,
                WeekdaySchedule.EVERY_DAY,
                from = monday.minusDays(3),
                to = monday,
            ),
        )
    }

    @Test
    fun `a completion on an unscheduled day does not count`() {
        // Stray rows must not push the rate above what the schedule allows.
        val schedule = WeekdaySchedule.toBitmask(listOf(DayOfWeek.MONDAY))

        assertEquals(
            0,
            HabitStatistics.completedDayCount(
                setOf(monday.minusDays(1)),
                schedule,
                from = monday.minusDays(7),
                to = monday,
            ),
        )
    }

    @Test
    fun `nothing due yet scores zero rather than dividing by zero`() {
        assertEquals(
            0f,
            HabitStatistics.completionRate(
                emptySet(),
                WeekdaySchedule.EVERY_DAY,
                from = monday,
                to = monday,
            ),
            0f,
        )
    }

    @Test
    fun `an unscheduled habit has no scheduled days`() {
        assertEquals(
            0,
            HabitStatistics.scheduledDayCount(
                emptySet(),
                WeekdaySchedule.NONE,
                from = monday.minusDays(30),
                to = monday,
            ),
        )
    }

    @Test
    fun `an inverted range yields nothing`() {
        assertEquals(
            0,
            HabitStatistics.scheduledDayCount(
                emptySet(),
                WeekdaySchedule.EVERY_DAY,
                from = monday,
                to = monday.minusDays(5),
            ),
        )
    }

    @Test
    fun `the rate never exceeds one`() {
        val completed = daysBefore(0, 1, 2, 3, 4, 5)

        val rate =
            HabitStatistics.completionRate(
                completed,
                WeekdaySchedule.EVERY_DAY,
                from = monday.minusDays(2),
                to = monday,
            )

        assertEquals(1f, rate, 0f)
    }

    @Test
    fun `a skipped day leaves the denominator rather than counting as a miss`() {
        // Four scheduled days, two kept, one skipped: the rate is 2 of 3, not 2 of 4. A skip that
        // counted as a miss would be indistinguishable from simply not ticking.
        val completed = daysBefore(1, 2)

        assertEquals(
            1f,
            HabitStatistics.completionRate(
                completed,
                WeekdaySchedule.EVERY_DAY,
                from = monday.minusDays(3),
                to = monday,
                skippedDates = daysBefore(3),
            ),
            0f,
        )
    }

    @Test
    fun `a day that is both completed and skipped counts as neither`() {
        // The repository keeps the two mutually exclusive, but the ratio must not depend on that:
        // a day counted in the numerator and not the denominator would push the rate above 100%.
        val completed = daysBefore(2, 3)

        assertEquals(
            1f,
            HabitStatistics.completionRate(
                completed,
                WeekdaySchedule.EVERY_DAY,
                from = monday.minusDays(3),
                to = monday.minusDays(1),
                skippedDates = daysBefore(2),
            ),
            0f,
        )
    }

    @Test
    fun `skipping the final day keeps the rate on the days that resolved`() {
        // The last day is already excluded while it is unfinished; skipping it must not make it
        // count, and must not disturb the days before it.
        val completed = daysBefore(1, 2)

        assertEquals(
            1f,
            HabitStatistics.completionRate(
                completed,
                WeekdaySchedule.EVERY_DAY,
                from = monday.minusDays(2),
                to = monday,
                skippedDates = daysBefore(0),
            ),
            0f,
        )
    }
}
