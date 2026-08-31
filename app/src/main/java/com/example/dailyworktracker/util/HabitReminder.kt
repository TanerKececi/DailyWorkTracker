package com.example.dailyworktracker.util

import com.example.dailyworktracker.data.local.entity.Habit
import java.time.LocalTime

/**
 * Reads and writes a habit's reminder as a single value.
 *
 * The entity stores the reminder as two independently nullable columns, which admits a half-set
 * state — an hour with no minute — that means nothing. Funnelling every access through these two
 * helpers keeps that state out of the rest of the app: a reminder is either a [LocalTime] or absent.
 */
val Habit.reminderTime: LocalTime?
    get() {
        val hour = reminderHour ?: return null
        val minute = reminderMinute ?: return null
        return LocalTime.of(hour, minute)
    }

fun Habit.withReminderTime(time: LocalTime?): Habit = copy(reminderHour = time?.hour, reminderMinute = time?.minute)
