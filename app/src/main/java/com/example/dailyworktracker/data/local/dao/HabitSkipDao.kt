package com.example.dailyworktracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.dailyworktracker.data.local.entity.HabitSkip
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitSkipDao {
    @Query("SELECT * FROM habit_skips WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun getSkip(
        habitId: Long,
        date: Long,
    ): HabitSkip?

    /** IGNORE keeps the unique (habitId, date) index authoritative if a double-swipe races. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(skip: HabitSkip)

    @Delete
    suspend fun delete(skip: HabitSkip)

    /** Removes a day's skip without having to read the row first. */
    @Query("DELETE FROM habit_skips WHERE habitId = :habitId AND date = :date")
    suspend fun deleteOn(
        habitId: Long,
        date: Long,
    )

    /** The days one habit was skipped. Unlike a completion, a skip has nothing to carry but its date. */
    @Query("SELECT date FROM habit_skips WHERE habitId = :habitId")
    fun observeSkipDates(habitId: Long): Flow<List<Long>>

    /**
     * Every skip across all habits, grouped in memory by the repository.
     *
     * Same reasoning as `observeAllCompletions`: one query the list screen can group beats a
     * per-habit query each time the list changes.
     */
    @Query("SELECT habitId, date FROM habit_skips")
    fun observeAllSkips(): Flow<List<HabitSkipDate>>
}

/** Minimal projection: which habit was skipped on which day. */
data class HabitSkipDate(
    val habitId: Long,
    val date: Long,
)
