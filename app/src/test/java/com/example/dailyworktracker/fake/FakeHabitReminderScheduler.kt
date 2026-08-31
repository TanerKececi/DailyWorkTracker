package com.example.dailyworktracker.fake

import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.reminder.HabitReminderScheduler

/** Records what it was asked to do, so tests can assert on scheduling without WorkManager. */
class FakeHabitReminderScheduler : HabitReminderScheduler {
    val scheduled = mutableListOf<Habit>()
    val cancelled = mutableListOf<Long>()

    override fun schedule(habit: Habit) {
        scheduled += habit
    }

    override fun cancel(habitId: Long) {
        cancelled += habitId
    }
}
