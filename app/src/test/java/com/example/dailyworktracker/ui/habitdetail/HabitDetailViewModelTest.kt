package com.example.dailyworktracker.ui.habitdetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
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

class HabitDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val monday = FakeDateProvider.MONDAY
    private val repository = FakeHabitRepository()
    private val dateProvider = FakeDateProvider()

    private fun viewModel(habitId: Long = 1L) =
        HabitDetailViewModel(
            repository = repository,
            dateProvider = dateProvider,
            savedStateHandle = SavedStateHandle(mapOf(HabitDetailViewModel.ARG_HABIT_ID to habitId)),
        )

    private suspend fun awaitDetail(habitId: Long = 1L): HabitDetailUiState {
        lateinit var result: HabitDetailUiState
        viewModel(habitId).uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            result = (awaitItem() as UiState.Success).data
        }
        return result
    }

    @Test
    fun `reports empty when the habit does not exist`() =
        runTest {
            viewModel(habitId = 99L).uiState.test {
                assertEquals(UiState.Loading, awaitItem())
                assertTrue(awaitItem() is UiState.Empty)
            }
        }

    @Test
    fun `exposes the habit's identity for the view to render`() =
        runTest {
            repository.seed(habit(id = 1L, title = "Do sport", emoji = "🏃"))

            val state = awaitDetail()

            assertEquals("Do sport", state.title)
            assertEquals("🏃", state.emoji)
            // The schedule stays a bitmask; formatting it needs a Context.
            assertEquals(WeekdaySchedule.EVERY_DAY, state.scheduleDaysBitmask)
        }

    @Test
    fun `reports current and longest streaks`() =
        runTest {
            repository.seed(habit(id = 1L))
            // A three-day run, a miss, then a two-day run ending today.
            repository.completeOn(1L, monday, monday.minusDays(1))
            repository.completeOn(1L, monday.minusDays(3), monday.minusDays(4), monday.minusDays(5))

            val state = awaitDetail()

            assertEquals(2, state.currentStreak)
            assertEquals(3, state.longestStreak)
        }

    @Test
    fun `counts every completion, including ones off the visible grid`() =
        runTest {
            repository.seed(habit(id = 1L))
            repository.completeOn(1L, monday, monday.minusDays(200))

            assertEquals(2, awaitDetail().completedCount)
        }

    @Test
    fun `the grid covers whole weeks so the weekday header lines up`() =
        runTest {
            repository.seed(habit(id = 1L))

            val heatmap = awaitDetail().heatmap

            assertEquals(HabitDetailViewModel.WEEKS_SHOWN * 7, heatmap.size)
            assertEquals(DayOfWeek.MONDAY, heatmap.first().date.dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, heatmap.last().date.dayOfWeek)
        }

    @Test
    fun `a new habit's grid starts at its own first week, not months of blank rows`() =
        runTest {
            repository.seed(
                habit(
                    id = 1L,
                    createdAt =
                        monday.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                ),
            )

            val heatmap = awaitDetail().heatmap

            // Created today, so only the current week is worth drawing.
            assertEquals(7, heatmap.size)
            assertEquals(monday, heatmap.first().date)
        }

    @Test
    fun `days before the habit existed are out of range`() =
        runTest {
            val createdOn = monday.minusDays(2)
            repository.seed(
                habit(
                    id = 1L,
                    createdAt =
                        createdOn.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                ),
            )

            val heatmap = awaitDetail().heatmap
            val byDate = heatmap.associateBy { it.date }

            assertEquals(DayStatus.OUT_OF_RANGE, byDate.getValue(createdOn.minusDays(1)).status)
            // The creation day itself is in range and, being unfinished, counts as missed.
            assertEquals(DayStatus.MISSED, byDate.getValue(createdOn).status)
        }

    @Test
    fun `future days are out of range`() =
        runTest {
            repository.seed(habit(id = 1L))

            val byDate = awaitDetail().heatmap.associateBy { it.date }

            assertEquals(DayStatus.OUT_OF_RANGE, byDate.getValue(monday.plusDays(1)).status)
        }

    @Test
    fun `an unfinished today is pending, not missed`() =
        runTest {
            repository.seed(habit(id = 1L))

            val byDate = awaitDetail().heatmap.associateBy { it.date }

            assertEquals(DayStatus.PENDING, byDate.getValue(monday).status)
        }

    @Test
    fun `completed and missed days are distinguished`() =
        runTest {
            repository.seed(habit(id = 1L))
            repository.completeOn(1L, monday.minusDays(1))

            val byDate = awaitDetail().heatmap.associateBy { it.date }

            assertEquals(DayStatus.COMPLETED, byDate.getValue(monday.minusDays(1)).status)
            assertEquals(DayStatus.MISSED, byDate.getValue(monday.minusDays(2)).status)
        }

    @Test
    fun `days the habit does not repeat on are marked unscheduled`() =
        runTest {
            repository.seed(
                habit(
                    id = 1L,
                    scheduleDaysBitmask = WeekdaySchedule.toBitmask(listOf(DayOfWeek.MONDAY)),
                ),
            )

            val byDate = awaitDetail().heatmap.associateBy { it.date }

            assertEquals(DayStatus.NOT_SCHEDULED, byDate.getValue(monday.minusDays(1)).status)
        }

    @Test
    fun `completion rate reflects kept days`() =
        runTest {
            val createdOn = monday.minusDays(3)
            repository.seed(
                habit(
                    id = 1L,
                    createdAt =
                        createdOn.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                ),
            )
            // Two of the three resolved days kept; today is unfinished and so is not counted.
            repository.completeOn(1L, monday.minusDays(1), monday.minusDays(2))

            assertEquals(2f / 3f, awaitDetail().completionRate, 0.001f)
        }
}
