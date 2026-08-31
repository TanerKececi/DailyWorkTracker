package com.example.dailyworktracker.ui.habitlist

import app.cash.turbine.test
import com.example.dailyworktracker.R
import com.example.dailyworktracker.fake.FakeDateProvider
import com.example.dailyworktracker.fake.FakeHabitRepository
import com.example.dailyworktracker.fake.habit
import com.example.dailyworktracker.testing.MainDispatcherRule
import com.example.dailyworktracker.ui.common.UiState
import com.example.dailyworktracker.util.WeekdaySchedule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    /** Created long before the test dates, so creation never accidentally hides a habit. */
    private fun oldHabit(
        id: Long = 1L,
        title: String = "Brush teeth",
        scheduleDaysBitmask: Int = WeekdaySchedule.EVERY_DAY,
    ) = habit(id = id, title = title, scheduleDaysBitmask = scheduleDaysBitmask, createdAt = 0L)

    @Test
    fun `starts on today`() {
        assertEquals(monday, viewModel().selectedDate.value)
    }

    @Test
    fun `starts in loading state`() =
        runTest {
            assertEquals(UiState.Loading, viewModel().uiState.value)
        }

    @Test
    fun `reports nothing created when there are no habits at all`() =
        runTest {
            viewModel().uiState.test {
                assertEquals(UiState.Loading, awaitItem())

                val empty = awaitItem() as UiState.Empty
                assertEquals(R.string.habit_list_empty_title, empty.titleRes)
            }
        }

    @Test
    fun `distinguishes nothing due from nothing created`() =
        runTest {
            repository.seed(
                oldHabit(scheduleDaysBitmask = WeekdaySchedule.toBitmask(listOf(DayOfWeek.WEDNESDAY))),
            )

            viewModel().uiState.test {
                assertEquals(UiState.Loading, awaitItem())

                val empty = awaitItem() as UiState.Empty
                assertEquals(R.string.habit_list_nothing_scheduled_title, empty.titleRes)
            }
        }

    @Test
    fun `emits habits scheduled for the selected day`() =
        runTest {
            repository.seed(oldHabit(title = "Brush teeth"))

            viewModel().uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                assertEquals(
                    listOf("Brush teeth"),
                    (awaitItem() as UiState.Success).data.map { it.title },
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
                assertEquals(UiState.Loading, awaitItem())
                assertTrue(awaitItem() is UiState.Empty)

                viewModel.onDatePicked(monday.minusDays(2))

                assertEquals(
                    listOf("Clean house"),
                    (awaitItem() as UiState.Success).data.map { it.title },
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
                assertEquals(UiState.Loading, awaitItem())
                awaitItem() // Today: one habit, not completed.

                // Yesterday looks identical until the toggle lands, and StateFlow conflates equal
                // values, so only the completion itself produces a new emission to await.
                viewModel.onPreviousDayClicked()
                viewModel.onHabitCheckedChanged(habitId = 1L)

                assertTrue((awaitItem() as UiState.Success).data.single().isCompleted)
            }

            assertEquals(listOf(yesterday), repository.completionsFor(habitId = 1L))
        }

    @Test
    fun `checking a habit on today still writes to today`() =
        runTest {
            repository.seed(oldHabit(id = 1L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                awaitItem()

                viewModel.onHabitCheckedChanged(habitId = 1L)

                assertTrue((awaitItem() as UiState.Success).data.single().isCompleted)
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
                assertEquals(UiState.Loading, awaitItem())
                assertTrue(awaitItem() is UiState.Success)

                viewModel.onPreviousDayClicked()

                assertTrue(awaitItem() is UiState.Empty)
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
                assertEquals(UiState.Loading, awaitItem())
                // As of today, yesterday's miss has broken the run.
                assertEquals(0, (awaitItem() as UiState.Success).data.single().currentStreak)

                viewModel.onDatePicked(monday.minusDays(2))

                // As of that day, the run was two long.
                assertEquals(2, (awaitItem() as UiState.Success).data.single().currentStreak)
            }
        }

    @Test
    fun `cannot step past today`() {
        val viewModel = viewModel()

        viewModel.onNextDayClicked()

        assertEquals(monday, viewModel.selectedDate.value)
    }

    @Test
    fun `picking a future date is clamped to today`() {
        val viewModel = viewModel()

        viewModel.onDatePicked(monday.plusDays(5))

        assertEquals(monday, viewModel.selectedDate.value)
    }

    @Test
    fun `stepping back then forward returns to today`() {
        val viewModel = viewModel()

        viewModel.onPreviousDayClicked()
        viewModel.onNextDayClicked()

        assertEquals(monday, viewModel.selectedDate.value)
    }

    @Test
    fun `jump to today returns from a past day`() {
        val viewModel = viewModel()

        viewModel.onDatePicked(monday.minusDays(6))
        viewModel.onTodayClicked()

        assertEquals(monday, viewModel.selectedDate.value)
    }

    @Test
    fun `resuming after midnight advances a selection that meant today`() {
        val viewModel = viewModel()
        val tuesday = monday.plusDays(1)

        dateProvider.advanceTo(tuesday)
        viewModel.onScreenResumed()

        assertEquals(tuesday, viewModel.selectedDate.value)
    }

    @Test
    fun `resuming after midnight leaves a deliberately chosen day alone`() {
        val viewModel = viewModel()
        val chosen = monday.minusDays(3)

        viewModel.onDatePicked(chosen)
        dateProvider.advanceTo(monday.plusDays(1))
        viewModel.onScreenResumed()

        assertEquals(chosen, viewModel.selectedDate.value)
    }

    @Test
    fun `archiving a habit removes it from the day`() =
        runTest {
            repository.seed(oldHabit(id = 1L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                awaitItem()

                viewModel.onHabitArchived(habitId = 1L)

                assertTrue(awaitItem() is UiState.Empty)
            }

            assertEquals(listOf(1L), repository.archivedIds)
        }

    @Test
    fun `passes the schedule through as a bitmask for the view to format`() =
        runTest {
            val mask = WeekdaySchedule.toBitmask(listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
            repository.seed(oldHabit(id = 1L, scheduleDaysBitmask = mask))

            viewModel().uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                assertEquals(mask, (awaitItem() as UiState.Success).data.single().scheduleDaysBitmask)
            }
        }
}
