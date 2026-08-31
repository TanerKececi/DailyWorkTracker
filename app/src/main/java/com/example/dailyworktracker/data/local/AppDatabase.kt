package com.example.dailyworktracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.dailyworktracker.data.local.dao.HabitCompletionDao
import com.example.dailyworktracker.data.local.dao.HabitDao
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.local.entity.HabitCompletion

/**
 * The app's single Room database.
 *
 * Dates are stored as epoch-day [Long]s rather than `LocalDate`, so no type converters are needed
 * here; the repository owns that conversion and exposes `LocalDate` to the rest of the app.
 */
@Database(
    entities = [Habit::class, HabitCompletion::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    abstract fun habitCompletionDao(): HabitCompletionDao

    companion object {
        const val DATABASE_NAME = "daily_work_tracker.db"
    }
}
