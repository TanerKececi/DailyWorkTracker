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
    suspend fun getCompletion(habitId: Long, date: Long): HabitCompletion?

    /** IGNORE keeps the unique (habitId, date) index authoritative if a double-tap races. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(completion: HabitCompletion)

    @Delete
    suspend fun delete(completion: HabitCompletion)

    /** Completion dates (epoch days), newest first — the input for streak calculation. */
    @Query("SELECT date FROM habit_completions WHERE habitId = :habitId ORDER BY date DESC")
    fun observeCompletionDates(habitId: Long): Flow<List<Long>>
}
