package com.example.dailyworktracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class WeekdayScheduleTest {
    @Test
    fun `every day mask includes all seven days`() {
        DayOfWeek.entries.forEach { day ->
            assertTrue(
                "Expected $day to be scheduled",
                WeekdaySchedule.isScheduledOn(WeekdaySchedule.EVERY_DAY, day),
            )
        }
    }

    @Test
    fun `none mask includes no days`() {
        DayOfWeek.entries.forEach { day ->
            assertFalse(WeekdaySchedule.isScheduledOn(WeekdaySchedule.NONE, day))
        }
    }

    @Test
    fun `each day maps to its own distinct bit`() {
        // Guards the value - 1 shift: an off-by-one would make two days collide.
        val masks = DayOfWeek.entries.map { WeekdaySchedule.toBitmask(listOf(it)) }
        assertEquals(DayOfWeek.entries.size, masks.toSet().size)
    }

    @Test
    fun `toBitmask and toDays round trip`() {
        val days = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        val mask = WeekdaySchedule.toBitmask(days)
        assertEquals(days, WeekdaySchedule.toDays(mask))
    }

    @Test
    fun `toDays returns days in calendar order regardless of input order`() {
        val mask = WeekdaySchedule.toBitmask(listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY))
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), WeekdaySchedule.toDays(mask))
    }

    @Test
    fun `withDay adds a day without disturbing the others`() {
        val friday = WeekdaySchedule.toBitmask(listOf(DayOfWeek.FRIDAY))
        val withMonday = WeekdaySchedule.withDay(friday, DayOfWeek.MONDAY, scheduled = true)

        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            WeekdaySchedule.toDays(withMonday),
        )
    }

    @Test
    fun `withDay removes only the requested day`() {
        val result =
            WeekdaySchedule.withDay(
                WeekdaySchedule.EVERY_DAY,
                DayOfWeek.WEDNESDAY,
                scheduled = false,
            )

        assertFalse(WeekdaySchedule.isScheduledOn(result, DayOfWeek.WEDNESDAY))
        assertEquals(6, WeekdaySchedule.toDays(result).size)
    }

    @Test
    fun `withDay never sets bits outside the seven day range`() {
        // The mask is persisted, so stray high bits would corrupt stored schedules.
        val result =
            WeekdaySchedule.withDay(
                WeekdaySchedule.EVERY_DAY,
                DayOfWeek.SUNDAY,
                scheduled = false,
            )

        assertEquals(0, result and WeekdaySchedule.EVERY_DAY.inv())
    }

    @Test
    fun `withDay is idempotent`() {
        val once = WeekdaySchedule.withDay(WeekdaySchedule.NONE, DayOfWeek.TUESDAY, true)
        val twice = WeekdaySchedule.withDay(once, DayOfWeek.TUESDAY, true)
        assertEquals(once, twice)
    }

    @Test
    fun `isEveryDay is true only for the full mask`() {
        assertTrue(WeekdaySchedule.isEveryDay(WeekdaySchedule.EVERY_DAY))
        assertFalse(
            WeekdaySchedule.isEveryDay(
                WeekdaySchedule.withDay(WeekdaySchedule.EVERY_DAY, DayOfWeek.MONDAY, false),
            ),
        )
    }

    @Test
    fun `hasAnyDay distinguishes empty from non-empty schedules`() {
        assertFalse(WeekdaySchedule.hasAnyDay(WeekdaySchedule.NONE))
        assertTrue(
            WeekdaySchedule.hasAnyDay(WeekdaySchedule.toBitmask(listOf(DayOfWeek.SATURDAY))),
        )
    }
}
