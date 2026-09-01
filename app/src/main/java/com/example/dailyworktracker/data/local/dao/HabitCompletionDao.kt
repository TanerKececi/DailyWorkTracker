package com.example.dailyworktracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.dailyworktracker.data.local.entity.HabitCompletion
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitCompletionDao {
    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun getCompletion(
        habitId: Long,
        date: Long,
    ): HabitCompletion?

    /** IGNORE keeps the unique (habitId, date) index authoritative if a double-tap races. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(completion: HabitCompletion)

    /** Overwrites the amount logged for a day that already has a row. */
    @Query("UPDATE habit_completions SET amount = :amount WHERE habitId = :habitId AND date = :date")
    suspend fun updateAmount(
        habitId: Long,
        date: Long,
        amount: Int,
    )

    @Delete
    suspend fun delete(completion: HabitCompletion)

    /**
     * Completions for one habit, newest first - the input for streak calculation and the grid.
     *
     * Carries the amount so the history grid can label a day with what was actually done. Streaks
     * still only need which days have a row at all.
     */
    @Query("SELECT date, amount FROM habit_completions WHERE habitId = :habitId ORDER BY date DESC")
    fun observeCompletions(habitId: Long): Flow<List<CompletionOnDate>>

    /**
     * Every completion across all habits.
     *
     * The list screen needs a streak per habit, and one query the repository groups in memory beats
     * a per-habit query each time the list changes. Personal habit histories stay small enough that
     * this is cheaper than the alternative.
     */
    @Query("SELECT habitId, date, amount FROM habit_completions")
    fun observeAllCompletions(): Flow<List<HabitCompletionDate>>
}

/** Minimal projection: which habit was completed on which day, and how much was logged. */
data class HabitCompletionDate(
    val habitId: Long,
    val date: Long,
    val amount: Int?,
)

/** One habit's completion: the day, and what was logged on it. */
data class CompletionOnDate(
    val date: Long,
    val amount: Int?,
)
