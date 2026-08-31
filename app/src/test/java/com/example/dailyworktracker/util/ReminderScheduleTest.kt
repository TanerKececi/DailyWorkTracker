package com.example.dailyworktracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderScheduleTest {
    private val eightAm = LocalTime.of(8, 0)

    private val monWedFri =
        WeekdaySchedule.toBitmask(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )

    private fun next(
        bitmask: Int = WeekdaySchedule.EVERY_DAY,
        time: LocalTime = eightAm,
        after: LocalDateTime,
    ) = ReminderSchedule.nextOccurrence(bitmask, time, after)

    @Test
    fun `fires later today when the time has not passed`() {
        assertEquals(
            MONDAY.atTime(8, 0),
            next(after = MONDAY.atTime(7, 59)),
        )
    }

    @Test
    fun `waits for tomorrow once today's time has passed`() {
        assertEquals(
            TUESDAY.atTime(8, 0),
            next(after = MONDAY.atTime(8, 1)),
        )
    }

    @Test
    fun `the reminder's own moment does not count as its next occurrence`() {
        // The worker wakes at exactly the reminder time and asks what comes next. Answering with
        // the occurrence it is already handling would put it in a loop, firing over and over.
        assertEquals(
            TUESDAY.atTime(8, 0),
            next(after = MONDAY.atTime(8, 0)),
        )
    }

    @Test
    fun `skips days the habit does not repeat on`() {
        // Monday 08:00 is gone, and Tuesday is not a scheduled day.
        assertEquals(
            WEDNESDAY.atTime(8, 0),
            next(bitmask = monWedFri, after = MONDAY.atTime(9, 0)),
        )
    }

    @Test
    fun `waits a full week for a habit on a single weekday`() {
        val sundayOnly = WeekdaySchedule.toBitmask(listOf(DayOfWeek.SUNDAY))

        assertEquals(
            SUNDAY.plusWeeks(1).atTime(8, 0),
            next(bitmask = sundayOnly, after = SUNDAY.atTime(8, 30)),
        )
    }

    @Test
    fun `finds the next scheduled day from an unscheduled one`() {
        val weekend = WeekdaySchedule.toBitmask(listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))

        assertEquals(
            SATURDAY.atTime(8, 0),
            next(bitmask = weekend, after = MONDAY.atTime(12, 0)),
        )
    }

    @Test
    fun `a habit on no days has no next occurrence`() {
        assertNull(next(bitmask = WeekdaySchedule.NONE, after = MONDAY.atTime(7, 0)))
    }

    @Test
    fun `crosses the end of the year`() {
        val newYearsEve = LocalDate.of(2026, 12, 31)

        assertEquals(
            LocalDate.of(2027, 1, 1).atTime(8, 0),
            next(after = newYearsEve.atTime(20, 0)),
        )
    }

    @Test
    fun `a minute past midnight is still today`() {
        assertEquals(
            MONDAY.atTime(0, 1),
            next(time = LocalTime.of(0, 1), after = MONDAY.atStartOfDay()),
        )
    }

    private companion object {
        val MONDAY: LocalDate = LocalDate.of(2026, 8, 31)
        val TUESDAY: LocalDate = MONDAY.plusDays(1)
        val WEDNESDAY: LocalDate = MONDAY.plusDays(2)
        val SATURDAY: LocalDate = MONDAY.plusDays(5)
        val SUNDAY: LocalDate = MONDAY.plusDays(6)
    }
}
