package com.example.dailyworktracker.ui.habitlist

import app.cash.turbine.test
import com.example.dailyworktracker.R
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
import java.time.LocalDate

class HabitListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val monday = LocalDate.of(2026, 8, 31)
    private val repository = FakeHabitRepository(today = monday)

    private fun viewModel() = HabitListViewModel(repository)

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
    fun `distinguishes nothing due today from nothing created`() =
        runTest {
            // Regression guard: this once claimed "No habits yet" while a habit existed.
            repository.seed(
                habit(scheduleDaysBitmask = WeekdaySchedule.toBitmask(listOf(DayOfWeek.WEDNESDAY))),
            )

            viewModel().uiState.test {
                assertEquals(UiState.Loading, awaitItem())

                val empty = awaitItem() as UiState.Empty
                assertEquals(R.string.habit_list_nothing_today_title, empty.titleRes)
            }
        }

    @Test
    fun `emits habits scheduled for today`() =
        runTest {
            repository.seed(habit(id = 1L, title = "Brush teeth"))

            viewModel().uiState.test {
                assertEquals(UiState.Loading, awaitItem())

                val success = awaitItem() as UiState.Success
                assertEquals(listOf("Brush teeth"), success.data.map { it.title })
                assertTrue(success.data.single().isCompleted.not())
            }
        }

    @Test
    fun `hides habits that do not repeat today`() =
        runTest {
            repository.seed(
                habit(id = 1L, title = "Due today"),
                habit(
                    id = 2L,
                    title = "Due Wednesday",
                    scheduleDaysBitmask = WeekdaySchedule.toBitmask(listOf(DayOfWeek.WEDNESDAY)),
                ),
            )

            viewModel().uiState.test {
                assertEquals(UiState.Loading, awaitItem())

                val success = awaitItem() as UiState.Success
                assertEquals(listOf("Due today"), success.data.map { it.title })
            }
        }

    @Test
    fun `checking a habit marks it completed for today`() =
        runTest {
            repository.seed(habit(id = 1L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                assertTrue((awaitItem() as UiState.Success).data.single().isCompleted.not())

                viewModel.onHabitCheckedChanged(habitId = 1L)

                assertTrue((awaitItem() as UiState.Success).data.single().isCompleted)
            }

            assertEquals(listOf(monday), repository.completionsFor(habitId = 1L))
        }

    @Test
    fun `checking an already completed habit clears it`() =
        runTest {
            repository.seed(habit(id = 1L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                awaitItem() // Initial, not completed.

                viewModel.onHabitCheckedChanged(habitId = 1L)
                assertTrue((awaitItem() as UiState.Success).data.single().isCompleted)

                viewModel.onHabitCheckedChanged(habitId = 1L)
                assertTrue((awaitItem() as UiState.Success).data.single().isCompleted.not())
            }

            assertEquals(emptyList<LocalDate>(), repository.completionsFor(habitId = 1L))
        }

    @Test
    fun `archiving a habit removes it from today`() =
        runTest {
            repository.seed(habit(id = 1L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                awaitItem() // Success with the habit.

                viewModel.onHabitArchived(habitId = 1L)

                assertTrue(awaitItem() is UiState.Empty)
            }

            assertEquals(listOf(1L), repository.archivedIds)
        }

    @Test
    fun `passes the schedule through as a bitmask for the view to format`() =
        runTest {
            // The ViewModel must not resolve display text; that needs a Context.
            val mask = WeekdaySchedule.toBitmask(listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
            repository.seed(habit(id = 1L, scheduleDaysBitmask = mask))

            viewModel().uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                assertEquals(mask, (awaitItem() as UiState.Success).data.single().scheduleDaysBitmask)
            }
        }
}
