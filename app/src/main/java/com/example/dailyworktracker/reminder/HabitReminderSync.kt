package com.example.dailyworktracker.reminder

import com.example.dailyworktracker.data.repository.HabitRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-arms every habit's reminder from what is actually stored.
 *
 * Not for the ordinary case: WorkManager persists its own queue, so reboots and process death need
 * no help. This is for the two situations where the habits and the pending jobs can part company.
 * A backup restored onto a new device brings the habit database but not WorkManager's, leaving
 * reminders that exist in the UI and nowhere else. And a job's delay is measured in elapsed time,
 * not wall-clock time, so after a time zone change an already-queued reminder still fires at the
 * old offset until it next recomputes.
 *
 * Re-scheduling is idempotent — the same habit at the same moment yields the same occurrence — so
 * running this on every launch costs one query and changes nothing when nothing is wrong.
 */
@Singleton
class HabitReminderSync
    @Inject
    constructor(
        private val repository: HabitRepository,
        private val scheduler: HabitReminderScheduler,
    ) {
        suspend fun resyncAll() {
            // Archived habits go through too: the scheduler reads that as "cancel", which is what
            // repairs a restore that brought an archived habit back with a reminder time still set.
            repository.observeAllHabits().first().forEach(scheduler::schedule)
        }
    }
