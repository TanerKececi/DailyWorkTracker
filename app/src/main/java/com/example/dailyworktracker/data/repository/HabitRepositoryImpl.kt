package com.example.dailyworktracker.data.repository

import com.example.dailyworktracker.data.local.dao.HabitCompletionDao
import com.example.dailyworktracker.data.local.dao.HabitDao
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.local.entity.HabitCompletion
import com.example.dailyworktracker.data.model.HabitWithStatus
import com.example.dailyworktracker.util.DateProvider
import com.example.dailyworktracker.util.WeekdaySchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
        private val dateProvider: DateProvider,
    ) : HabitRepository {
        /**
         * Today is resolved inside [flow] so it is read when collection starts rather than when the
         * flow is constructed. A collection running across midnight keeps the previous day until it is
         * restarted, which is acceptable for v1 and is where a date-tick source would slot in later.
         */
        override fun observeTodaysHabits(): Flow<List<HabitWithStatus>> =
            flow {
                val today = dateProvider.today()
                emitAll(
                    habitDao.observeHabitsWithStatus(today.toEpochDay())
                        .map { habits ->
                            habits.filter {
                                WeekdaySchedule.isScheduledOn(it.habit.scheduleDaysBitmask, today.dayOfWeek)
                            }
                        },
                )
            }

        override fun observeActiveHabitCount(): Flow<Int> = habitDao.observeActiveHabitCount()

        override fun observeCompletionDates(habitId: Long): Flow<List<LocalDate>> =
            completionDao.observeCompletionDates(habitId)
                .map { dates -> dates.map(LocalDate::ofEpochDay) }

        override suspend fun getHabit(habitId: Long): Habit? = habitDao.getById(habitId)

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

        override suspend fun toggleCompletionToday(habitId: Long) = toggleCompletion(habitId, dateProvider.today())
    }
