package com.example.dailyworktracker.util

import com.example.dailyworktracker.fake.habit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class HabitReminderTest {
    @Test
    fun `reads a stored reminder as a time`() {
        val stored = habit().copy(reminderHour = 7, reminderMinute = 5)

        assertEquals(LocalTime.of(7, 5), stored.reminderTime)
    }

    @Test
    fun `no reminder reads as null`() {
        assertNull(habit().reminderTime)
    }

    @Test
    fun `a half-set reminder reads as no reminder`() {
        // The two columns are independently nullable, so this state is representable even though
        // nothing in the app writes it. Reading it as "7 o'clock" would invent a minute.
        assertNull(habit().copy(reminderHour = 7, reminderMinute = null).reminderTime)
        assertNull(habit().copy(reminderHour = null, reminderMinute = 5).reminderTime)
    }

    @Test
    fun `setting a reminder writes both columns`() {
        val updated = habit().withReminderTime(LocalTime.of(21, 30))

        assertEquals(21, updated.reminderHour)
        assertEquals(30, updated.reminderMinute)
    }

    @Test
    fun `clearing a reminder empties both columns`() {
        val updated = habit(reminderTime = LocalTime.of(21, 30)).withReminderTime(null)

        assertNull(updated.reminderHour)
        assertNull(updated.reminderMinute)
    }

    @Test
    fun `seconds are dropped so the stored time round-trips`() {
        val updated = habit().withReminderTime(LocalTime.of(21, 30, 45))

        assertEquals(LocalTime.of(21, 30), updated.reminderTime)
    }
}
