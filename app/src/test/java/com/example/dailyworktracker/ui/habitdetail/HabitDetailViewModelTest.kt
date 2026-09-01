package com.example.dailyworktracker.ui.habitdetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.dailyworktracker.fake.FakeDateProvider
import com.example.dailyworktracker.fake.FakeHabitRepository
import com.example.dailyworktracker.fake.habit
import com.example.dailyworktracker.testing.MainDispatcherRule
import com.example.dailyworktracker.ui.habitdetail.HabitDetailDisplayState.Content
import com.example.dailyworktracker.util.WeekdaySchedule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            assertEquals(HabitDetailDisplayState.Loading, awaitItem())
            result = (awaitItem() as Content).habit
        }
        return result
    }

    private suspend fun awaitDays(habitId: Long = 1L): List<HeatmapItem.Day> =
        awaitDetail(habitId).heatmap.filterIsInstance<HeatmapItem.Day>()

    @Test
    fun `a logged amount rides along with the day it belongs to`() =
        runTest {
            repository.seed(habit(id = 1L))
            repository.setAmount(habitId = 1L, date = monday.minusDays(1), amount = 12)

            val byDate = awaitDays().associateBy { it.date }

            assertEquals(12, byDate.getValue(monday.minusDays(1)).amount)
            // Nothing logged means the cell falls back to showing the date.
            assertNull(byDate.getValue(monday.minusDays(2)).amount)
        }

    @Test
    fun `a ticked-off habit carries no amounts`() =
        runTest {
            repository.seed(habit(id = 1L))
            repository.toggleCompletion(habitId = 1L, date = monday.minusDays(1))

            val byDate = awaitDays().associateBy { it.date }

            assertNull(byDate.getValue(monday.minusDays(1)).amount)
        }

    @Test
    fun `a whole row shares one band, and consecutive months alternate`() =
        runTest {
            repository.seed(habit(id = 1L))

            val rows = awaitDetail().heatmap.chunked(HabitDetailViewModel.COLUMNS)

            // Banding is per week, so a row never changes shade partway along.
            rows.forEach { row ->
                assertEquals(1, row.map { it.isAlternateMonth }.distinct().size)
            }

            // The shade flips exactly where a new month is announced, and nowhere else.
            rows.zipWithNext().forEach { (previous, next) ->
                val startsNewMonth = (next.first() as HeatmapItem.WeekGutter).month != null
                val changedBand = previous.first().isAlternateMonth != next.first().isAlternateMonth
                assertEquals(startsNewMonth, changedBand)
            }
        }

    @Test
    fun `a gutter shares the band of the week it labels`() =
        runTest {
            repository.seed(habit(id = 1L))

            val heatmap = awaitDetail().heatmap
            val gutters = heatmap.filterIsInstance<HeatmapItem.WeekGutter>()

            gutters.forEach { gutter ->
                val mondayCell =
                    heatmap.filterIsInstance<HeatmapItem.Day>()
                        .first { it.date == gutter.weekStart }
                assertEquals(mondayCell.isAlternateMonth, gutter.isAlternateMonth)
            }
        }

    @Test
    fun `reports empty when the habit does not exist`() =
        runTest {
            viewModel(habitId = 99L).uiState.test {
                assertEquals(HabitDetailDisplayState.Loading, awaitItem())
                assertEquals(HabitDetailDisplayState.Missing, awaitItem())
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
    fun `each row is a month gutter followed by its seven days`() =
        runTest {
            repository.seed(habit(id = 1L))

            val heatmap = awaitDetail().heatmap
            val days = heatmap.filterIsInstance<HeatmapItem.Day>()

            assertEquals(HabitDetailViewModel.WEEKS_SHOWN, heatmap.count { it is HeatmapItem.WeekGutter })
            assertEquals(HabitDetailViewModel.WEEKS_SHOWN * 7, days.size)
            // Every eighth slot is a gutter, which is what keeps the columns aligned.
            heatmap.filterIndexed { index, _ -> index % HabitDetailViewModel.COLUMNS == 0 }
                .forEach { assertTrue(it is HeatmapItem.WeekGutter) }
            assertEquals(DayOfWeek.MONDAY, days.first().date.dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, days.last().date.dayOfWeek)
        }

    @Test
    fun `a month is labelled once, on the week it starts`() =
        runTest {
            repository.seed(habit(id = 1L))

            val labelled =
                awaitDetail().heatmap
                    .filterIsInstance<HeatmapItem.WeekGutter>()
                    .mapNotNull { it.month }

            // Spanning twelve weeks, every month named must be distinct and in order.
            assertEquals(labelled.distinct(), labelled)
            assertEquals(labelled.sorted(), labelled)
            assertTrue("Expected at least one month heading", labelled.isNotEmpty())
        }

    @Test
    fun `the gutter carries the week it belongs to, so rows stay distinguishable`() =
        runTest {
            repository.seed(habit(id = 1L))

            val gutters = awaitDetail().heatmap.filterIsInstance<HeatmapItem.WeekGutter>()

            // Most gutters have no label; without this they would be indistinguishable to DiffUtil.
            assertEquals(gutters.size, gutters.map { it.weekStart }.distinct().size)
            gutters.forEach { assertEquals(DayOfWeek.MONDAY, it.weekStart.dayOfWeek) }
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
            assertEquals(1, heatmap.count { it is HeatmapItem.WeekGutter })
            assertEquals(monday, heatmap.filterIsInstance<HeatmapItem.Day>().first().date)
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
            val byDate = heatmap.filterIsInstance<HeatmapItem.Day>().associateBy { it.date }

            assertEquals(DayStatus.OUT_OF_RANGE, byDate.getValue(createdOn.minusDays(1)).status)
            // The creation day itself is in range and, being unfinished, counts as missed.
            assertEquals(DayStatus.MISSED, byDate.getValue(createdOn).status)
        }

    @Test
    fun `future days are out of range`() =
        runTest {
            repository.seed(habit(id = 1L))

            val byDate = awaitDays().associateBy { it.date }

            assertEquals(DayStatus.OUT_OF_RANGE, byDate.getValue(monday.plusDays(1)).status)
        }

    @Test
    fun `an unfinished today is pending, not missed`() =
        runTest {
            repository.seed(habit(id = 1L))

            val byDate = awaitDays().associateBy { it.date }

            assertEquals(DayStatus.PENDING, byDate.getValue(monday).status)
        }

    @Test
    fun `completed and missed days are distinguished`() =
        runTest {
            repository.seed(habit(id = 1L))
            repository.completeOn(1L, monday.minusDays(1))

            val byDate = awaitDays().associateBy { it.date }

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

            val byDate = awaitDays().associateBy { it.date }

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
