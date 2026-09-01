package com.example.dailyworktracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    abstract fun habitCompletionDao(): HabitCompletionDao

    companion object {
        const val DATABASE_NAME = "daily_work_tracker.db"

        /**
         * Adds the goal columns.
         *
         * Both are nullable with no default, so every row already in the database keeps its current
         * meaning: a habit with no unit is the tick-it-off kind, and a completion with no amount is
         * still simply a completed day.
         *
         * The database is built without a destructive fallback, so this migration is the only thing
         * standing between an existing install and a crash on upgrade.
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE habits ADD COLUMN goalUnit TEXT")
                    db.execSQL("ALTER TABLE habit_completions ADD COLUMN amount INTEGER")
                }
            }

        /**
         * Adds the part of the day a habit belongs to.
         *
         * Nullable with no default, so every existing habit reads as belonging to no particular
         * time - which is exactly what it was before the column existed.
         */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE habits ADD COLUMN timeOfDay TEXT")
                }
            }
    }
}
