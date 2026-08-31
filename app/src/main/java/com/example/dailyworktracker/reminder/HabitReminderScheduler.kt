package com.example.dailyworktracker.reminder

import com.example.dailyworktracker.data.local.entity.Habit

/**
 * Keeps a habit's pending reminder in step with the habit itself.
 *
 * Declared as an interface so [com.example.dailyworktracker.data.repository.HabitRepositoryImpl],
 * which calls it on every write, stays a plain class that unit tests can build without WorkManager
 * or a Context.
 */
interface HabitReminderScheduler {
    /**
     * Schedules [habit]'s next reminder, replacing whatever was already pending for it.
     *
     * A habit that is archived, has no reminder time, or repeats on no days has its reminder
     * cancelled instead, so callers can hand over any habit after any change without deciding first.
     */
    fun schedule(habit: Habit)

    fun cancel(habitId: Long)
}
