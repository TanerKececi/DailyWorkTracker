package com.example.dailyworktracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.model.HabitWithStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert
    suspend fun insert(habit: Habit): Long

    @Update
    suspend fun update(habit: Habit)

    /** Soft delete, so the habit's completion history survives. */
    @Query("UPDATE habits SET isArchived = 1 WHERE id = :habitId")
    suspend fun archive(habitId: Long)

    @Query("UPDATE habits SET isArchived = 0 WHERE id = :habitId")
    suspend fun unarchive(habitId: Long)

    /** Every habit regardless of schedule or archived state, active ones first. */
    @Query("SELECT * FROM habits ORDER BY isArchived ASC, createdAt ASC")
    fun observeAllHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :habitId")
    suspend fun getById(habitId: Long): Habit?

    /**
     * How many habits exist at all, regardless of schedule. Lets the UI tell "you have not created
     * anything yet" apart from "nothing is due today".
     */
    @Query("SELECT COUNT(*) FROM habits WHERE isArchived = 0")
    fun observeActiveHabitCount(): Flow<Int>

    /**
     * Observes all active habits, each flagged with whether it was completed on [date]
     * (a `LocalDate.toEpochDay()` value).
     *
     * Scheduling is deliberately *not* filtered here: the bitmask is easier to reason about and test
     * in Kotlin than in SQL, so the repository narrows this list down to the habits due on [date].
     */
    @Query(
        """
        SELECT h.*,
               EXISTS(
                   SELECT 1 FROM habit_completions c
                   WHERE c.habitId = h.id AND c.date = :date
               ) AS isCompleted
        FROM habits h
        WHERE h.isArchived = 0
        ORDER BY h.createdAt ASC
        """,
    )
    fun observeHabitsWithStatus(date: Long): Flow<List<HabitWithStatus>>
}
