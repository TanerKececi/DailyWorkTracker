package com.example.dailyworktracker.data.repository

import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.model.HabitWithStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Single source of truth for habits and their completions.
 *
 * Declared as an interface so ViewModels can be unit tested against a fake without Room or Android.
 * Dates cross this boundary as [LocalDate]; the epoch-day encoding is a storage detail below it.
 */
interface HabitRepository {
    /** Active habits scheduled for today, each flagged with whether it is already done. */
    fun observeTodaysHabits(): Flow<List<HabitWithStatus>>

    /** Count of all non-archived habits, whatever their schedule. */
    fun observeActiveHabitCount(): Flow<Int>

    /** Every habit, including archived ones, for the management screen. */
    fun observeAllHabits(): Flow<List<Habit>>

    fun observeCompletionDates(habitId: Long): Flow<List<LocalDate>>

    suspend fun getHabit(habitId: Long): Habit?

    suspend fun addHabit(habit: Habit): Long

    suspend fun updateHabit(habit: Habit)

    /** Soft delete: hides the habit while preserving its completion history. */
    suspend fun archiveHabit(habitId: Long)

    suspend fun unarchiveHabit(habitId: Long)

    /** Marks [habitId] done on [date], or clears it if it was already done. */
    suspend fun toggleCompletion(
        habitId: Long,
        date: LocalDate,
    )

    /** Convenience for the common case, so callers need no clock of their own. */
    suspend fun toggleCompletionToday(habitId: Long)
}
