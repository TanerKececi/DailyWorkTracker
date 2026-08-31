package com.example.dailyworktracker.data.repository

import com.example.dailyworktracker.data.local.dao.HabitCompletionDao
import com.example.dailyworktracker.data.local.dao.HabitDao
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.local.entity.HabitCompletion
import com.example.dailyworktracker.data.model.TodayHabit
import com.example.dailyworktracker.reminder.HabitReminderScheduler
import com.example.dailyworktracker.util.HabitVisibility
import com.example.dailyworktracker.util.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminder scheduling hangs off every write here rather than off the screens that trigger them.
 * This is the one place a habit can change, so it is the only place where the pending reminder and
 * the stored habit cannot drift apart — archiving from the today list and from All habits are two
 * call sites that would each have had to remember.
 */
@Singleton
class HabitRepositoryImpl
    @Inject
    constructor(
        private val habitDao: HabitDao,
        private val completionDao: HabitCompletionDao,
        private val reminderScheduler: HabitReminderScheduler,
    ) : HabitRepository {
        override fun observeHabitsFor(date: LocalDate): Flow<List<TodayHabit>> =
            combine(
                habitDao.observeHabitsWithStatus(date.toEpochDay()),
                completionDao.observeAllCompletions(),
            ) { habits, completions ->
                val datesByHabit =
                    completions
                        .groupBy({ it.habitId }, { LocalDate.ofEpochDay(it.date) })
                        .mapValues { (_, dates) -> dates.toSet() }

                habits
                    .filter { HabitVisibility.isActiveOn(it.habit, date) }
                    .map { habitWithStatus ->
                        TodayHabit(
                            habit = habitWithStatus.habit,
                            isCompleted = habitWithStatus.isCompleted,
                            currentStreak =
                                StreakCalculator.currentStreak(
                                    completedDates = datesByHabit[habitWithStatus.habit.id].orEmpty(),
                                    scheduleDaysBitmask = habitWithStatus.habit.scheduleDaysBitmask,
                                    asOf = date,
                                ),
                        )
                    }
            }

        override fun observeActiveHabitCount(): Flow<Int> = habitDao.observeActiveHabitCount()

        override fun observeCompletionDates(habitId: Long): Flow<List<LocalDate>> =
            completionDao.observeCompletionDates(habitId)
                .map { dates -> dates.map(LocalDate::ofEpochDay) }

        override suspend fun getHabit(habitId: Long): Habit? = habitDao.getById(habitId)

        override fun observeHabit(habitId: Long): Flow<Habit?> = habitDao.observeById(habitId)

        override suspend fun isCompletedOn(
            habitId: Long,
            date: LocalDate,
        ): Boolean = completionDao.getCompletion(habitId, date.toEpochDay()) != null

        override suspend fun addHabit(habit: Habit): Long {
            val id = habitDao.insert(habit)
            // Room assigns the id, so the scheduler needs the stored habit, not the one passed in.
            reminderScheduler.schedule(habit.copy(id = id))
            return id
        }

        override suspend fun updateHabit(habit: Habit) {
            habitDao.update(habit)
            reminderScheduler.schedule(habit)
        }

        override fun observeAllHabits(): Flow<List<Habit>> = habitDao.observeAllHabits()

        override suspend fun archiveHabit(habitId: Long) {
            habitDao.archive(habitId)
            reminderScheduler.cancel(habitId)
        }

        override suspend fun unarchiveHabit(habitId: Long) {
            habitDao.unarchive(habitId)
            // Re-read rather than trusting a caller's copy: only the stored row knows the reminder.
            habitDao.getById(habitId)?.let(reminderScheduler::schedule)
        }

        override suspend fun toggleCompletion(
            habitId: Long,
            date: LocalDate,
        ) {
            val epochDay = date.toEpochDay()
            val existing = completionDao.getCompletion(habitId, epochDay)
            if (existing != null) {
                completionDao.delete(existing)
            } else {
                completionDao.insert(
                    HabitCompletion(
                        habitId = habitId,
                        date = epochDay,
                        completedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
