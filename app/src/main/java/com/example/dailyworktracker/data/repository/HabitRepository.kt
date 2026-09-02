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

    /** Completed days for one habit, each with the amount logged on it (null when none was). */
    fun observeCompletions(habitId: Long): Flow<Map<LocalDate, Int?>>

    /**
     * Days one habit was deliberately skipped.
     *
     * A plain set of dates, not a map: unlike a completion, a skip has nothing to record but that
     * it happened.
     */
    fun observeSkips(habitId: Long): Flow<Set<LocalDate>>

    suspend fun getHabit(habitId: Long): Habit?

    /**
     * Whether [habitId] was already ticked off on [date].
     *
     * A one-shot read rather than a Flow: the reminder worker asks once, at the moment it
     * wakes, and has nothing to keep watching for.
     */
    suspend fun isCompletedOn(
        habitId: Long,
        date: LocalDate,
    ): Boolean

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

    /**
     * Records [amount] against [habitId] on [date] for an amount habit.
     *
     * An amount of zero or less removes the record, which is how a mistake is corrected. Any other
     * amount marks the day done: the number is what was achieved, not a target to clear.
     */
    suspend fun setAmount(
        habitId: Long,
        date: LocalDate,
        amount: Int,
    )

    /**
     * Marks [habitId] as deliberately skipped on [date], or clears the skip if it was already
     * skipped.
     *
     * Skipping a day that was done clears the completion, and completing a skipped day clears the
     * skip. The two are mutually exclusive, and this boundary is the only place that has to know
     * it: a day is either done, deliberately not done, or simply unresolved.
     */
    suspend fun toggleSkip(
        habitId: Long,
        date: LocalDate,
    )
}
