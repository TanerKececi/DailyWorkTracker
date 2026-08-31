package com.example.dailyworktracker.ui.allhabits

import app.cash.turbine.test
import com.example.dailyworktracker.fake.FakeHabitRepository
import com.example.dailyworktracker.fake.habit
import com.example.dailyworktracker.testing.MainDispatcherRule
import com.example.dailyworktracker.ui.common.UiState
import com.example.dailyworktracker.util.WeekdaySchedule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class AllHabitsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeHabitRepository(today = LocalDate.of(2026, 8, 31)) // Monday.

    private fun viewModel() = AllHabitsViewModel(repository)

    @Test
    fun `lists habits that today's screen filters out`() =
        runTest {
            // The reason this screen exists: habits not due today must stay reachable.
            repository.seed(
                habit(
                    id = 1L,
                    title = "Due Wednesday",
                    scheduleDaysBitmask = WeekdaySchedule.toBitmask(listOf(DayOfWeek.WEDNESDAY)),
                ),
            )

            viewModel().uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                assertEquals(
                    listOf("Due Wednesday"),
                    (awaitItem() as UiState.Success).data.map { it.title },
                )
            }
        }

    @Test
    fun `includes archived habits so they can be restored`() =
        runTest {
            repository.seed(habit(id = 1L, title = "Archived one", isArchived = true))

            viewModel().uiState.test {
                assertEquals(UiState.Loading, awaitItem())

                val item = (awaitItem() as UiState.Success).data.single()
                assertTrue(item.isArchived)
            }
        }

    @Test
    fun `orders active habits before archived ones`() =
        runTest {
            repository.seed(
                habit(id = 1L, title = "Archived", createdAt = 0L, isArchived = true),
                habit(id = 2L, title = "Active", createdAt = 1L),
            )

            viewModel().uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                assertEquals(
                    listOf("Active", "Archived"),
                    (awaitItem() as UiState.Success).data.map { it.title },
                )
            }
        }

    @Test
    fun `toggling an active habit archives it`() =
        runTest {
            repository.seed(habit(id = 1L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                assertFalse((awaitItem() as UiState.Success).data.single().isArchived)

                viewModel.onArchiveToggled(habitId = 1L, isCurrentlyArchived = false)

                assertTrue((awaitItem() as UiState.Success).data.single().isArchived)
            }

            assertEquals(listOf(1L), repository.archivedIds)
        }

    @Test
    fun `toggling an archived habit restores it`() =
        runTest {
            repository.seed(habit(id = 1L, isArchived = true))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                assertTrue((awaitItem() as UiState.Success).data.single().isArchived)

                viewModel.onArchiveToggled(habitId = 1L, isCurrentlyArchived = true)

                assertFalse((awaitItem() as UiState.Success).data.single().isArchived)
            }

            assertEquals(listOf(1L), repository.unarchivedIds)
        }

    @Test
    fun `reports empty when no habits exist`() =
        runTest {
            viewModel().uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                assertTrue(awaitItem() is UiState.Empty)
            }
        }
}
