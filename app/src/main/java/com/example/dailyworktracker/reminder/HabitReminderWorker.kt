package com.example.dailyworktracker.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.util.DateProvider
import com.example.dailyworktracker.util.HabitVisibility
import com.example.dailyworktracker.util.reminderTime
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Posts one habit's reminder, then schedules the next one.
 *
 * Whether to actually notify is decided here rather than when the reminder was scheduled, because
 * everything that matters can change in between: the habit may have been ticked off already,
 * archived, rescheduled onto other weekdays, or deleted outright. Deciding at fire time means a
 * stale pending job can never produce a wrong notification, only a wasted wake-up.
 */
@HiltWorker
class HabitReminderWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted parameters: WorkerParameters,
        private val repository: HabitRepository,
        private val notifier: HabitReminderNotifier,
        private val scheduler: HabitReminderScheduler,
        private val dateProvider: DateProvider,
    ) : CoroutineWorker(context, parameters) {
        override suspend fun doWork(): Result {
            val habitId = inputData.getLong(KEY_HABIT_ID, NO_HABIT_ID)
            // The habit is gone: let the chain end here rather than scheduling another wake-up.
            val habit = repository.getHabit(habitId) ?: return Result.success()

            val today = dateProvider.today()
            val isDue = habit.reminderTime != null && HabitVisibility.isActiveOn(habit, today)
            if (isDue && !repository.isCompletedOn(habitId, today)) {
                notifier.notifyHabitDue(habit)
            }

            // Always chain, even on a day this habit was not due: the scheduler works out whether
            // there is a next occurrence at all, and cancels when there is not.
            scheduler.schedule(habit)
            return Result.success()
        }

        companion object {
            const val KEY_HABIT_ID = "habitId"

            private const val NO_HABIT_ID = -1L
        }
    }
