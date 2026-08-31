package com.example.dailyworktracker.util

import com.example.dailyworktracker.fake.habit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class HabitVisibilityTest {
    private val monday = LocalDate.of(2026, 8, 31)
    private val utc: ZoneId = ZoneOffset.UTC

    private fun createdOn(date: LocalDate): Long = date.atStartOfDay(utc).toInstant().toEpochMilli()

    @Test
    fun `a scheduled habit created earlier is visible`() {
        val subject = habit(createdAt = createdOn(monday.minusDays(10)))

        assertTrue(HabitVisibility.isActiveOn(subject, monday, utc))
    }

    @Test
    fun `a habit is hidden on days before it was created`() {
        // Otherwise browsing back shows a wall of misses for a habit that did not exist yet.
        val subject = habit(createdAt = createdOn(monday))

        assertFalse(HabitVisibility.isActiveOn(subject, monday.minusDays(1), utc))
    }

    @Test
    fun `a habit is visible on its own creation day`() {
        val subject = habit(createdAt = createdOn(monday))

        assertTrue(HabitVisibility.isActiveOn(subject, monday, utc))
    }

    @Test
    fun `a habit is hidden on a weekday it does not repeat on`() {
        val subject =
            habit(
                scheduleDaysBitmask = WeekdaySchedule.toBitmask(listOf(DayOfWeek.WEDNESDAY)),
                createdAt = createdOn(monday.minusDays(10)),
            )

        assertFalse(HabitVisibility.isActiveOn(subject, monday, utc))
        assertTrue(HabitVisibility.isActiveOn(subject, monday.plusDays(2), utc))
    }

    @Test
    fun `an archived habit is hidden even on a past day it was due`() {
        // Archiving means "not part of my routine"; resurrecting it in history would contradict that.
        val subject = habit(createdAt = createdOn(monday.minusDays(10)), isArchived = true)

        assertFalse(HabitVisibility.isActiveOn(subject, monday.minusDays(1), utc))
    }

    @Test
    fun `creation time is interpreted in the given zone`() {
        // 23:00 UTC on Sunday is already Monday in Tokyo, so the visible creation day differs.
        val sundayLateUtc =
            monday.minusDays(1).atTime(23, 0).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        val subject = habit(createdAt = sundayLateUtc)

        assertTrue(HabitVisibility.isActiveOn(subject, monday.minusDays(1), utc))
        assertFalse(
            HabitVisibility.isActiveOn(subject, monday.minusDays(1), ZoneId.of("Asia/Tokyo")),
        )
    }
}
