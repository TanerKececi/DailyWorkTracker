package com.example.dailyworktracker.widget

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dailyworktracker.MainActivity
import com.example.dailyworktracker.data.local.dao.HabitDao
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.model.HabitGoal
import com.example.dailyworktracker.data.model.HabitUnit
import com.example.dailyworktracker.data.model.withGoal
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.util.DateProvider
import com.example.dailyworktracker.util.WeekdaySchedule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * What happens when a habit's row is tapped on the home screen.
 *
 * Unit tests cannot reach this: the receiver is unexported, resolves its dependencies through an
 * entry point rather than a constructor, and finishes its work on a coroutine after `goAsync()`.
 * Broadcasting for real is the only way to exercise the path the launcher actually takes - and an
 * instrumentation test runs as the app, so it is allowed to reach an unexported receiver.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HabitWidgetActionReceiverTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: HabitRepository

    @Inject
    lateinit var habitDao: HabitDao

    @Inject
    lateinit var dateProvider: DateProvider

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { habitDao.deleteAll() }
    }

    @After
    fun tearDown() {
        runBlocking { habitDao.deleteAll() }
    }

    @Test
    fun tickedOffHabitIsCompletedInPlace() =
        runBlocking {
            val habitId = repository.addHabit(habit())

            context.sendBroadcast(toggleIntent(habitId))

            assertTrue(
                "Tapping a ticked-off habit on the home screen should complete it",
                awaitCompleted(habitId, expected = true),
            )
        }

    /**
     * The reason this branch exists: a number cannot be typed on the home screen, so completing the
     * habit there would record a day as done with nothing logged against it.
     */
    @Test
    fun amountHabitOpensTheAppInsteadOfBeingCompleted() =
        runBlocking {
            val habitId = repository.addHabit(habit().withGoal(HabitGoal.Amount(HabitUnit.PAGES)))
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)

            try {
                context.sendBroadcast(toggleIntent(habitId))

                assertNotNull(
                    "Tapping an amount habit on the home screen should open the app",
                    monitor.waitForActivityWithTimeout(ACTIVITY_TIMEOUT_MILLIS),
                )
                assertFalse(
                    "It must not record a completion with no amount",
                    awaitCompleted(habitId, expected = true),
                )
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }

    private fun habit() =
        Habit(
            title = "Read",
            emoji = "📚",
            scheduleDaysBitmask = WeekdaySchedule.EVERY_DAY,
            createdAt = 0L,
        )

    private fun toggleIntent(habitId: Long) =
        Intent(context, HabitWidgetActionReceiver::class.java)
            .setAction(HabitWidgetActionReceiver.ACTION_TOGGLE)
            .putExtra(HabitWidgetActionReceiver.EXTRA_HABIT_ID, habitId)

    /**
     * The receiver finishes on a coroutine after `goAsync()`, so the write lands some time after
     * the broadcast returns. Polls rather than sleeping a fixed time: the pass case is quick, and
     * only the failing case waits the whole timeout.
     */
    private suspend fun awaitCompleted(
        habitId: Long,
        expected: Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + COMPLETION_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (repository.isCompletedOn(habitId, dateProvider.today()) == expected) return expected
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        return !expected
    }

    private companion object {
        const val COMPLETION_TIMEOUT_MILLIS = 3_000L
        const val ACTIVITY_TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
