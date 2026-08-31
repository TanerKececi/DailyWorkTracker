package com.example.dailyworktracker.data.repository

import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.model.TodayHabit
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Single source of truth for habits and their completions.
 *
 * Declared as an interface so ViewModels can be unit tested against a fake without Room or Android.
 * Dates cross this boundary as [LocalDate]; the epoch-day encoding is a storage detail below it.
 */
interface HabitRepository {
    /**
     * Habits due on [date], each with its completion state and its streak as it stood that day.
     *
     * Habits created after [date] are excluded, so browsing back in time does not invent missed days
     * for habits that did not exist yet.
     */
    fun observeHabitsFor(date: LocalDate): Flow<List<TodayHabit>>

    /** Count of all non-archived habits, whatever their schedule. */
    fun observeActiveHabitCount(): Flow<Int>

    /** Every habit, including archived ones, for the management screen. */
    fun observeAllHabits(): Flow<List<Habit>>

    fun observeCompletionDates(habitId: Long): Flow<List<LocalDate>>

    suspend fun getHabit(habitId: Long): Habit?

    /** Emits null once the habit no longer exists, so a detail screen can close itself. */
    fun observeHabit(habitId: Long): Flow<Habit?>

    suspend fun addHabit(habit: Habit): Long

    suspend fun updateHabit(habit: Habit)

    /** Soft delete: hides the habit while preserving its completion history. */
    suspend fun archiveHabit(habitId: Long)

    suspend fun unarchiveHabit(habitId: Long)

    /**
     * Marks [habitId] done on [date], or clears it if it was already done.
     *
     * The date is always explicit. A "toggle today" convenience used to exist, but once the screen
     * can show any day it becomes a second write path with its own clock, free to disagree with the
     * date the user is actually looking at.
     */
    suspend fun toggleCompletion(
        habitId: Long,
        date: LocalDate,
    )
}
