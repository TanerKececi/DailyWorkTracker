package com.example.dailyworktracker.ui.habitlist

import app.cash.turbine.test
import com.example.dailyworktracker.data.model.HabitGoal
import com.example.dailyworktracker.data.model.HabitUnit
import com.example.dailyworktracker.data.model.withGoal
import com.example.dailyworktracker.fake.FakeDateProvider
import com.example.dailyworktracker.fake.FakeHabitRepository
import com.example.dailyworktracker.fake.habit
import com.example.dailyworktracker.testing.MainDispatcherRule
import com.example.dailyworktracker.ui.habitlist.HabitListDisplayState.Content
import com.example.dailyworktracker.util.WeekdaySchedule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

class HabitListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val monday = FakeDateProvider.MONDAY
    private val repository = FakeHabitRepository()
    private val dateProvider = FakeDateProvider()

    private fun viewModel() = HabitListViewModel(repository, dateProvider)

    /**
     * A ViewModel with something collecting its state, which is what a live screen is.
     *
     * The state flow shares `WhileSubscribed`, so with no collector `uiState.value` stays frozen at
     * the value it was created with. Tests that read `.value` have to subscribe first, exactly as a
     * Fragment does.
     */
    private fun TestScope.subscribed(): HabitListViewModel =
        viewModel().also { viewModel ->
            backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()
        }

    /** Created long before the test dates, so creation never accidentally hides a habit. */
    private fun oldHabit(
        id: Long = 1L,
        title: String = "Brush teeth",
        scheduleDaysBitmask: Int = WeekdaySchedule.EVERY_DAY,
    ) = habit(id = id, title = title, scheduleDaysBitmask = scheduleDaysBitmask, createdAt = 0L)

    @Test
    fun `starts on today`() =
        runTest {
            assertEquals(monday, subscribed().uiState.value.selectedDate)
        }

    @Test
    fun `starts in loading state`() =
        runTest {
            assertEquals(HabitListDisplayState.Loading, viewModel().uiState.value.displayState)
        }

    /**
     * The date bar and the list are one value.
     *
     * This is the reason the screen has a single state object: they used to be two StateFlows, so
     * the view could render a day label from one and a list from the other and show them disagreeing.
     */
    @Test
    fun `the date bar and the list arrive in the same emission`() =
        runTest {
            repository.seed(oldHabit(title = "Brush teeth"))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)

                val today = awaitItem()
                assertEquals(monday, today.selectedDate)
                assertEquals(monday, today.today)
                assertFalse(today.canGoToNextDay)
                assertFalse(today.showJumpToToday)
                assertEquals(
                    listOf("Brush teeth"),
                    (today.displayState as Content).habits.map { it.title },
                )

                viewModel.onPreviousDayClicked()

                val yesterday = awaitItem()
                assertEquals(monday.minusDays(1), yesterday.selectedDate)
                assertTrue(yesterday.canGoToNextDay)
                assertTrue(yesterday.showJumpToToday)
                assertTrue(yesterday.displayState is Content)
            }
        }

    @Test
    fun `reports nothing created when there are no habits at all`() =
        runTest {
            viewModel().uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                assertEquals(HabitListDisplayState.NoHabitsYet, awaitItem().displayState)
            }
        }

    @Test
    fun `distinguishes nothing due from nothing created`() =
        runTest {
            repository.seed(
                oldHabit(scheduleDaysBitmask = WeekdaySchedule.toBitmask(listOf(DayOfWeek.WEDNESDAY))),
            )

            viewModel().uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                assertEquals(HabitListDisplayState.NothingScheduled, awaitItem().displayState)
            }
        }

    @Test
    fun `emits habits scheduled for the selected day`() =
        runTest {
            repository.seed(oldHabit(title = "Brush teeth"))

            viewModel().uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                assertEquals(
                    listOf("Brush teeth"),
                    (awaitItem().displayState as Content).habits.map { it.title },
                )
            }
        }

    @Test
    fun `selecting a past day loads that day's schedule`() =
        runTest {
            // Due Saturdays only; today is Monday, so it appears only once we step back to Saturday.
            repository.seed(
                oldHabit(
                    title = "Clean house",
                    scheduleDaysBitmask = WeekdaySchedule.toBitmask(listOf(DayOfWeek.SATURDAY)),
                ),
            )
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                assertEquals(HabitListDisplayState.NothingScheduled, awaitItem().displayState)

                viewModel.onDatePicked(monday.minusDays(2))

                assertEquals(
                    listOf("Clean house"),
                    (awaitItem().displayState as Content).habits.map { it.title },
                )
            }
        }

    @Test
    fun `checking a habit on a past day writes to that day, not today`() =
        runTest {
            // The whole point of the feature: backfill must not land on the wrong date.
            repository.seed(oldHabit(id = 1L))
            val viewModel = viewModel()
            val yesterday = monday.minusDays(1)

            viewModel.uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                awaitItem() // Today: one habit, not completed.

                viewModel.onPreviousDayClicked()
                awaitItem() // Yesterday, still not completed.
                viewModel.onHabitCheckedChanged(habitId = 1L)

                assertTrue((awaitItem().displayState as Content).habits.single().isCompleted)
            }

            assertEquals(listOf(yesterday), repository.completionsFor(habitId = 1L))
        }

    @Test
    fun `checking a habit on today still writes to today`() =
        runTest {
            repository.seed(oldHabit(id = 1L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                awaitItem()

                viewModel.onHabitCheckedChanged(habitId = 1L)

                assertTrue((awaitItem().displayState as Content).habits.single().isCompleted)
            }

            assertEquals(listOf(monday), repository.completionsFor(habitId = 1L))
        }

    @Test
    fun `a habit created today is absent from yesterday`() =
        runTest {
            val createdToday = monday.atStartOfDay(java.time.ZoneId.systemDefault())
            repository.seed(habit(id = 1L, createdAt = createdToday.toInstant().toEpochMilli()))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                assertTrue(awaitItem().displayState is Content)

                viewModel.onPreviousDayClicked()

                assertEquals(HabitListDisplayState.NothingScheduled, awaitItem().displayState)
            }
        }

    @Test
    fun `streak is reported as of the selected day`() =
        runTest {
            repository.seed(oldHabit(id = 1L))
            // Completed the two days before yesterday, but not yesterday itself.
            repository.completeOn(1L, monday.minusDays(2), monday.minusDays(3))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                // As of today, yesterday's miss has broken the run.
                assertEquals(0, (awaitItem().displayState as Content).habits.single().currentStreak)

                viewModel.onDatePicked(monday.minusDays(2))

                // As of that day, the run was two long.
                assertEquals(2, (awaitItem().displayState as Content).habits.single().currentStreak)
            }
        }

    @Test
    fun `cannot step past today`() =
        runTest {
            val viewModel = subscribed()

            viewModel.onNextDayClicked()
            runCurrent()

            assertEquals(monday, viewModel.uiState.value.selectedDate)
        }

    @Test
    fun `picking a future date is clamped to today`() =
        runTest {
            val viewModel = subscribed()

            viewModel.onDatePicked(monday.plusDays(5))
            runCurrent()

            assertEquals(monday, viewModel.uiState.value.selectedDate)
        }

    @Test
    fun `stepping back then forward returns to today`() =
        runTest {
            val viewModel = subscribed()

            viewModel.onPreviousDayClicked()
            runCurrent()
            viewModel.onNextDayClicked()
            runCurrent()

            assertEquals(monday, viewModel.uiState.value.selectedDate)
        }

    @Test
    fun `jump to today returns from a past day`() =
        runTest {
            val viewModel = subscribed()

            viewModel.onDatePicked(monday.minusDays(6))
            runCurrent()
            viewModel.onTodayClicked()
            runCurrent()

            assertEquals(monday, viewModel.uiState.value.selectedDate)
        }

    @Test
    fun `resuming after midnight advances a selection that meant today`() =
        runTest {
            val viewModel = subscribed()
            val tuesday = monday.plusDays(1)

            dateProvider.advanceTo(tuesday)
            viewModel.onScreenResumed()
            runCurrent()

            assertEquals(tuesday, viewModel.uiState.value.selectedDate)
        }

    @Test
    fun `resuming after midnight leaves a deliberately chosen day alone`() =
        runTest {
            val viewModel = subscribed()
            val chosen = monday.minusDays(3)

            viewModel.onDatePicked(chosen)
            runCurrent()
            dateProvider.advanceTo(monday.plusDays(1))
            viewModel.onScreenResumed()
            runCurrent()

            assertEquals(chosen, viewModel.uiState.value.selectedDate)
        }

    /**
     * Today moves even when the selection does not.
     *
     * The view used to ask the ViewModel for today's date whenever it drew the date bar. Now the
     * date travels in the state, so it has to advance on its own, or a screen left open past
     * midnight would keep offering to step forward into a day that has already arrived.
     */
    @Test
    fun `resuming after midnight advances today even on a chosen day`() =
        runTest {
            val viewModel = subscribed()
            val chosen = monday.minusDays(3)

            viewModel.onDatePicked(chosen)
            runCurrent()
            dateProvider.advanceTo(monday.plusDays(1))
            viewModel.onScreenResumed()
            runCurrent()

            assertEquals(monday.plusDays(1), viewModel.uiState.value.today)
        }

    /**
     * The same rule as ticking a box: the entry lands on the day being viewed, not on today.
     *
     * This is the failure the whole backfill feature exists to avoid, and an amount habit is a
     * second write path that could get it wrong independently.
     */
    @Test
    fun `logging an amount on a past day writes to that day`() =
        runTest {
            repository.seed(oldHabit(id = 1L, title = "Read").withGoal(HabitGoal.Amount(HabitUnit.PAGES)))
            val viewModel = viewModel()
            val yesterday = monday.minusDays(1)

            viewModel.uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                awaitItem()

                viewModel.onPreviousDayClicked()
                awaitItem()
                viewModel.onAmountEntered(habitId = 1L, amount = 12)

                val row = (awaitItem().displayState as Content).habits.single()
                assertEquals(12, row.amount)
                assertTrue(row.isCompleted)
            }

            assertEquals(listOf(yesterday), repository.completionsFor(habitId = 1L))
        }

    @Test
    fun `logging zero clears the day`() =
        runTest {
            repository.seed(oldHabit(id = 1L, title = "Read").withGoal(HabitGoal.Amount(HabitUnit.PAGES)))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                awaitItem()

                viewModel.onAmountEntered(habitId = 1L, amount = 12)
                assertTrue((awaitItem().displayState as Content).habits.single().isCompleted)

                viewModel.onAmountEntered(habitId = 1L, amount = 0)

                val row = (awaitItem().displayState as Content).habits.single()
                assertFalse(row.isCompleted)
                assertNull(row.amount)
            }
        }

    @Test
    fun `archiving a habit removes it from the day`() =
        runTest {
            repository.seed(oldHabit(id = 1L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                awaitItem()

                viewModel.onHabitArchived(habitId = 1L)

                assertEquals(HabitListDisplayState.NoHabitsYet, awaitItem().displayState)
            }

            assertEquals(listOf(1L), repository.archivedIds)
        }

    @Test
    fun `passes the schedule through as a bitmask for the view to format`() =
        runTest {
            val mask = WeekdaySchedule.toBitmask(listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
            repository.seed(oldHabit(id = 1L, scheduleDaysBitmask = mask))

            viewModel().uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                assertEquals(
                    mask,
                    (awaitItem().displayState as Content).habits.single().scheduleDaysBitmask,
                )
            }
        }
}
