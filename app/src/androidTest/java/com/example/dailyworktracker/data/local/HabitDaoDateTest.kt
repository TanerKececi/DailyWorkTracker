package com.example.dailyworktracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dailyworktracker.data.local.dao.HabitCompletionDao
import com.example.dailyworktracker.data.local.dao.HabitDao
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.local.entity.HabitCompletion
import com.example.dailyworktracker.util.WeekdaySchedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Exercises [HabitDao.observeHabitsWithStatus] for days other than today.
 *
 * The query always took a date parameter, but until backfill existed it was only ever called with
 * today, so its per-date `EXISTS` subquery was effectively untested. Unit tests cannot reach it —
 * they run against a fake — so this is the layer where a wrong date would otherwise slip through.
 */
@RunWith(AndroidJUnit4::class)
class HabitDaoDateTest {
    private lateinit var database: AppDatabase
    private lateinit var habitDao: HabitDao
    private lateinit var completionDao: HabitCompletionDao

    private val monday = LocalDate.of(2026, 8, 31)

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            ).build()
        habitDao = database.habitDao()
        completionDao = database.habitCompletionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertDailyHabit(): Long =
        habitDao.insert(
            Habit(
                title = "Brush teeth",
                emoji = "🪥",
                scheduleDaysBitmask = WeekdaySchedule.EVERY_DAY,
                createdAt = 0L,
            ),
        )

    private suspend fun complete(
        habitId: Long,
        date: LocalDate,
    ) = completionDao.insert(
        HabitCompletion(habitId = habitId, date = date.toEpochDay(), completedAt = 0L),
    )

    @Test
    fun completionIsReportedOnlyForTheDayItWasRecorded() =
        runTest {
            val habitId = insertDailyHabit()
            val yesterday = monday.minusDays(1)

            complete(habitId, yesterday)

            val onYesterday = habitDao.observeHabitsWithStatus(yesterday.toEpochDay()).first()
            val onMonday = habitDao.observeHabitsWithStatus(monday.toEpochDay()).first()

            assertTrue("Completion should show on the day it was recorded", onYesterday.single().isCompleted)
            assertFalse("Completion must not leak into another day", onMonday.single().isCompleted)
        }

    @Test
    fun completionsOnDifferentDaysAreIndependent() =
        runTest {
            val habitId = insertDailyHabit()
            val twoDaysAgo = monday.minusDays(2)

            complete(habitId, monday)
            complete(habitId, twoDaysAgo)

            assertTrue(habitDao.observeHabitsWithStatus(monday.toEpochDay()).first().single().isCompleted)
            assertFalse(
                habitDao.observeHabitsWithStatus(monday.minusDays(1).toEpochDay()).first().single().isCompleted,
            )
            assertTrue(
                habitDao.observeHabitsWithStatus(twoDaysAgo.toEpochDay()).first().single().isCompleted,
            )
        }

    @Test
    fun removingACompletionClearsOnlyThatDay() =
        runTest {
            val habitId = insertDailyHabit()
            val yesterday = monday.minusDays(1)
            complete(habitId, monday)
            complete(habitId, yesterday)

            val stored = completionDao.getCompletion(habitId, yesterday.toEpochDay())
            completionDao.delete(checkNotNull(stored))

            assertTrue(habitDao.observeHabitsWithStatus(monday.toEpochDay()).first().single().isCompleted)
            assertFalse(
                habitDao.observeHabitsWithStatus(yesterday.toEpochDay()).first().single().isCompleted,
            )
        }

    @Test
    fun theUniqueIndexKeepsADoubleCompletionIdempotent() =
        runTest {
            val habitId = insertDailyHabit()

            complete(habitId, monday)
            complete(habitId, monday)

            assertEquals(1, completionDao.observeCompletionDates(habitId).first().size)
        }
}
