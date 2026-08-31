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
}
