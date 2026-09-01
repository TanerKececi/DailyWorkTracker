package com.example.dailyworktracker.data.repository

import com.example.dailyworktracker.data.local.dao.HabitCompletionDao
import com.example.dailyworktracker.data.local.dao.HabitDao
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.local.entity.HabitCompletion
import com.example.dailyworktracker.data.model.TodayHabit
import com.example.dailyworktracker.reminder.HabitReminderScheduler
import com.example.dailyworktracker.util.HabitVisibility
import com.example.dailyworktracker.util.StreakCalculator
import com.example.dailyworktracker.widget.HabitWidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminder scheduling and widget refreshes hang off every write here rather than off the
 * screens that trigger them. This is the one place a habit or a completion can change, so it
 * is the only place where what is stored and what the outside world shows cannot drift apart
 * — archiving alone is reachable from two screens, and each would otherwise have had to
 * remember both.
 */
@Singleton
class HabitRepositoryImpl
    @Inject
    constructor(
        private val habitDao: HabitDao,
        private val completionDao: HabitCompletionDao,
        private val reminderScheduler: HabitReminderScheduler,
        private val widgetUpdater: HabitWidgetUpdater,
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

                // Only the day being shown needs its amount; streaks still care only about which
                // days have a row at all, which is why they keep taking a plain Set<LocalDate>.
                val amountsOnDate =
                    completions
                        .filter { it.date == date.toEpochDay() }
                        .associateBy({ it.habitId }, { it.amount })

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
                            amount = amountsOnDate[habitWithStatus.habit.id],
                        )
                    }
            }

        override fun observeActiveHabitCount(): Flow<Int> = habitDao.observeActiveHabitCount()

        override fun observeCompletions(habitId: Long): Flow<Map<LocalDate, Int?>> =
            completionDao.observeCompletions(habitId)
                .map { rows -> rows.associateBy({ LocalDate.ofEpochDay(it.date) }, { it.amount }) }

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
            widgetUpdater.onHabitsChanged()
            return id
        }

        override suspend fun updateHabit(habit: Habit) {
            habitDao.update(habit)
            reminderScheduler.schedule(habit)
            widgetUpdater.onHabitsChanged()
        }

        override fun observeAllHabits(): Flow<List<Habit>> = habitDao.observeAllHabits()

        override suspend fun archiveHabit(habitId: Long) {
            habitDao.archive(habitId)
            reminderScheduler.cancel(habitId)
            widgetUpdater.onHabitsChanged()
        }

        override suspend fun unarchiveHabit(habitId: Long) {
            habitDao.unarchive(habitId)
            // Re-read rather than trusting a caller's copy: only the stored row knows the reminder.
            habitDao.getById(habitId)?.let(reminderScheduler::schedule)
            widgetUpdater.onHabitsChanged()
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
            widgetUpdater.onHabitsChanged()
        }

        override suspend fun setAmount(
            habitId: Long,
            date: LocalDate,
            amount: Int,
        ) {
            val epochDay = date.toEpochDay()
            val existing = completionDao.getCompletion(habitId, epochDay)

            when {
                // Clearing the amount clears the day: there is no "done, but zero pages".
                amount <= 0 -> existing?.let { completionDao.delete(it) }
                existing != null -> completionDao.updateAmount(habitId, epochDay, amount)
                else ->
                    completionDao.insert(
                        HabitCompletion(
                            habitId = habitId,
                            date = epochDay,
                            completedAt = System.currentTimeMillis(),
                            amount = amount,
                        ),
                    )
            }
            widgetUpdater.onHabitsChanged()
        }
    }
