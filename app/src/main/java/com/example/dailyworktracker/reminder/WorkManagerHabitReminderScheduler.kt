package com.example.dailyworktracker.reminder

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.util.DateProvider
import com.example.dailyworktracker.util.ReminderSchedule
import com.example.dailyworktracker.util.reminderTime
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules reminders as a chain of one-shot WorkManager jobs: each run posts its notification and
 * then enqueues the next occurrence.
 *
 * A `PeriodicWorkRequest` would be the obvious fit but is the wrong one twice over. It cannot
 * express "only on the days this habit repeats", and its period restarts from when the previous run
 * *finished*, so every minute a run is delayed is a minute the reminder moves later — a week of Doze
 * and an 08:00 reminder is arriving after lunch. Recomputing the next occurrence from the wall clock
 * each time means lateness never accumulates.
 *
 * Work is keyed by a unique name per habit, which is what stops an edit from leaving two reminders
 * running for the same habit. WorkManager persists it, so reminders survive a reboot without a
 * BOOT_COMPLETED receiver.
 */
@Singleton
class WorkManagerHabitReminderScheduler
    @Inject
    constructor(
        private val workManager: WorkManager,
        private val dateProvider: DateProvider,
    ) : HabitReminderScheduler {
        override fun schedule(habit: Habit) {
            val now = dateProvider.now()
            val next =
                habit.reminderTime
                    ?.takeUnless { habit.isArchived }
                    ?.let { ReminderSchedule.nextOccurrence(habit.scheduleDaysBitmask, it, now) }

            if (next == null) {
                cancel(habit.id)
                return
            }

            val delay = Duration.between(now, next).coerceAtLeast(Duration.ZERO)
            workManager.enqueueUniqueWork(
                workName(habit.id),
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<HabitReminderWorker>()
                    .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(HabitReminderWorker.KEY_HABIT_ID to habit.id))
                    .build(),
            )
        }

        override fun cancel(habitId: Long) {
            workManager.cancelUniqueWork(workName(habitId))
        }

        private companion object {
            fun workName(habitId: Long): String = "habit-reminder-$habitId"
        }
    }
