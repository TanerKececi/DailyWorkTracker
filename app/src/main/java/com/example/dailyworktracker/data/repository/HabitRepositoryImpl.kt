package com.example.dailyworktracker.data.repository

import com.example.dailyworktracker.data.local.dao.HabitCompletionDao
import com.example.dailyworktracker.data.local.dao.HabitDao
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.local.entity.HabitCompletion
import com.example.dailyworktracker.data.model.TodayHabit
import com.example.dailyworktracker.util.HabitVisibility
import com.example.dailyworktracker.util.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepositoryImpl
    @Inject
    constructor(
        private val habitDao: HabitDao,
        private val completionDao: HabitCompletionDao,
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

        override suspend fun addHabit(habit: Habit): Long = habitDao.insert(habit)

        override suspend fun updateHabit(habit: Habit) = habitDao.update(habit)

        override fun observeAllHabits(): Flow<List<Habit>> = habitDao.observeAllHabits()

        override suspend fun archiveHabit(habitId: Long) = habitDao.archive(habitId)

        override suspend fun unarchiveHabit(habitId: Long) = habitDao.unarchive(habitId)

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
