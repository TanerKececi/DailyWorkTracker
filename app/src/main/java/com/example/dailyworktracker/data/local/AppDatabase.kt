package com.example.dailyworktracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.dailyworktracker.data.local.dao.HabitCompletionDao
import com.example.dailyworktracker.data.local.dao.HabitDao
import com.example.dailyworktracker.data.local.dao.HabitSkipDao
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.local.entity.HabitCompletion
import com.example.dailyworktracker.data.local.entity.HabitSkip

/**
 * The app's single Room database.
 *
 * Dates are stored as epoch-day [Long]s rather than `LocalDate`, so no type converters are needed
 * here; the repository owns that conversion and exposes `LocalDate` to the rest of the app.
 */
@Database(
    entities = [Habit::class, HabitCompletion::class, HabitSkip::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    abstract fun habitCompletionDao(): HabitCompletionDao

    abstract fun habitSkipDao(): HabitSkipDao

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

        /**
         * Adds the skipped-days table.
         *
         * Purely additive: no existing table is touched, so every habit and completion already
         * stored keeps its exact meaning, and a database that has never skipped anything simply
         * has an empty table.
         */
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `habit_skips` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`habitId` INTEGER NOT NULL, " +
                            "`date` INTEGER NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_skips_habitId_date` " +
                            "ON `habit_skips` (`habitId`, `date`)",
                    )
                }
            }
    }
}
