package com.example.dailyworktracker.data.repository

import com.example.dailyworktracker.data.local.dao.HabitCompletionDao
import com.example.dailyworktracker.data.local.dao.HabitDao
import com.example.dailyworktracker.data.local.dao.HabitSkipDao
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.local.entity.HabitCompletion
import com.example.dailyworktracker.data.local.entity.HabitSkip
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
        private val skipDao: HabitSkipDao,
        private val reminderScheduler: HabitReminderScheduler,
        private val widgetUpdater: HabitWidgetUpdater,
    ) : HabitRepository {
        override fun observeHabitsFor(date: LocalDate): Flow<List<TodayHabit>> =
            combine(
                habitDao.observeHabitsWithStatus(date.toEpochDay()),
                completionDao.observeAllCompletions(),
                skipDao.observeAllSkips(),
            ) { habits, completions, skips ->
                val datesByHabit =
                    completions
                        .groupBy({ it.habitId }, { LocalDate.ofEpochDay(it.date) })
                        .mapValues { (_, dates) -> dates.toSet() }

                val skipsByHabit =
                    skips
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
                        val skipped = skipsByHabit[habitWithStatus.habit.id].orEmpty()
                        TodayHabit(
                            habit = habitWithStatus.habit,
                            isCompleted = habitWithStatus.isCompleted,
                            currentStreak =
                                StreakCalculator.currentStreak(
                                    completedDates = datesByHabit[habitWithStatus.habit.id].orEmpty(),
                                    scheduleDaysBitmask = habitWithStatus.habit.scheduleDaysBitmask,
                                    asOf = date,
                                    skippedDates = skipped,
                                ),
                            amount = amountsOnDate[habitWithStatus.habit.id],
                            isSkipped = date in skipped,
                        )
                    }
            }

        override fun observeActiveHabitCount(): Flow<Int> = habitDao.observeActiveHabitCount()

        override fun observeCompletions(habitId: Long): Flow<Map<LocalDate, Int?>> =
            completionDao.observeCompletions(habitId)
                .map { rows -> rows.associateBy({ LocalDate.ofEpochDay(it.date) }, { it.amount }) }

        override fun observeSkips(habitId: Long): Flow<Set<LocalDate>> =
            skipDao.observeSkipDates(habitId)
                .map { days -> days.mapTo(mutableSetOf(), LocalDate::ofEpochDay) }

        override fun observeAllCompletionDates(): Flow<Map<Long, Set<LocalDate>>> =
            completionDao.observeAllCompletions()
                .map { rows -> groupByHabit(rows.map { it.habitId to it.date }) }

        override fun observeAllSkipDates(): Flow<Map<Long, Set<LocalDate>>> =
            skipDao.observeAllSkips()
                .map { rows -> groupByHabit(rows.map { it.habitId to it.date }) }

        /** Epoch days become LocalDates here, the same boundary every other date crosses. */
        private fun groupByHabit(rows: List<Pair<Long, Long>>): Map<Long, Set<LocalDate>> =
            rows.groupBy({ it.first }, { LocalDate.ofEpochDay(it.second) })
                .mapValues { (_, dates) -> dates.toSet() }

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
                // Doing it settles the day, so it is no longer one deliberately passed on.
                skipDao.deleteOn(habitId, epochDay)
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
            // Logging an amount is doing it, so it clears a skip for the same reason ticking does.
            if (amount > 0) skipDao.deleteOn(habitId, epochDay)
            widgetUpdater.onHabitsChanged()
        }

        override suspend fun toggleSkip(
            habitId: Long,
            date: LocalDate,
        ) {
            val epochDay = date.toEpochDay()
            val existing = skipDao.getSkip(habitId, epochDay)
            if (existing != null) {
                skipDao.delete(existing)
            } else {
                skipDao.insert(
                    HabitSkip(
                        habitId = habitId,
                        date = epochDay,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                // Passing on a day undoes having done it, so the two can never both be true.
                completionDao.getCompletion(habitId, epochDay)?.let { completionDao.delete(it) }
            }
            widgetUpdater.onHabitsChanged()
        }
    }
