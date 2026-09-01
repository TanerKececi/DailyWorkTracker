package com.example.dailyworktracker.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Upgrade coverage for an existing install.
 *
 * The database is built without a destructive fallback, so a missing or wrong migration does not
 * degrade gracefully - it throws on first open and the app is dead for everyone who already had it.
 * Unit tests cannot reach this: they run against a fake repository, and the in-memory Room tests
 * always start at the current version. Only a real v1 file exercised through the migration will do.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    private val date = LocalDate.of(2026, 8, 31)

    /**
     * A habit and its completion written by the previous release must survive, and must keep
     * meaning what they meant: no unit is the tick-it-off kind, and the completion row alone is
     * still what makes the day done.
     */
    @Test
    fun migrate1To2_keepsExistingHabitsAndCompletions() =
        runTest {
            helper.createDatabase(TEST_DB, 1).use { db ->
                db.execSQL(
                    """
                    INSERT INTO habits (id, title, emoji, colorHex, scheduleDaysBitmask,
                                        reminderHour, reminderMinute, createdAt, isArchived)
                    VALUES (1, 'Brush teeth', '🪥', NULL, 127, NULL, NULL, 0, 0)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO habit_completions (id, habitId, date, completedAt)
                    VALUES (1, 1, ${date.toEpochDay()}, 0)
                    """.trimIndent(),
                )
            }

            // Throws if the migration does not produce exactly the schema Room expects for v2.
            helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

            val database = migratedDatabase()
            val habit = database.habitDao().getById(1L)
            assertEquals("Brush teeth", habit?.title)
            assertNull("A habit from v1 has no unit, so it is the checkbox kind", habit?.goalUnit)

            val completion = database.habitCompletionDao().getCompletion(1L, date.toEpochDay())
            assertTrue("The completion row must survive the upgrade", completion != null)
            assertNull("A completion from v1 records no amount", completion?.amount)

            // The property the whole feature rests on: a row existing still means "done".
            val completedDates = database.habitCompletionDao().observeCompletionDates(1L).first()
            assertEquals(listOf(date.toEpochDay()), completedDates)
        }

    /** Opens the migrated file through Room itself, so the entities and the columns must agree. */
    private fun migratedDatabase(): AppDatabase =
        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB,
        ).addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
            .also(helper::closeWhenFinished)

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
