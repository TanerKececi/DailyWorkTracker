package com.example.dailyworktracker.ui.habitlist

import app.cash.turbine.test
import com.example.dailyworktracker.data.model.HabitGoal
import com.example.dailyworktracker.data.model.HabitUnit
import com.example.dailyworktracker.data.model.TimeOfDay
import com.example.dailyworktracker.data.model.withGoal
import com.example.dailyworktracker.data.model.withTimeOfDay
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
import java.time.LocalDate
import java.time.ZoneId

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

    /** The strip's range comes from habit creation dates, which are stored as epoch millis. */
    private fun LocalDate.toEpochMilli(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

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
    fun `filtering to a part of the day narrows the list`() =
        runTest {
            repository.seed(
                oldHabit(id = 1L, title = "Brush teeth").withTimeOfDay(TimeOfDay.MORNING),
                oldHabit(id = 2L, title = "Read").withTimeOfDay(TimeOfDay.EVENING),
            )
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                // All day shows both.
                assertEquals(2, (awaitItem().displayState as Content).habits.size)

                viewModel.onTimeFilterSelected(TimeOfDay.MORNING)

                val morning = awaitItem()
                assertEquals(TimeOfDay.MORNING, morning.timeFilter)
                assertEquals(
                    listOf("Brush teeth"),
                    (morning.displayState as Content).habits.map { it.title },
                )
            }
        }

    /**
     * A habit tied to no particular time appears only under "all day".
     *
     * Letting those through every filter would put most of the list behind every chip, which is
     * the same as having no filter at all.
     */
    @Test
    fun `a habit with no part of the day is hidden by a filter`() =
        runTest {
            repository.seed(oldHabit(id = 1L, title = "Brush teeth"))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                assertTrue(awaitItem().displayState is Content)

                viewModel.onTimeFilterSelected(TimeOfDay.MORNING)

                // Due today, just not in this part of the day - which is its own empty, not
                // "nothing scheduled" and certainly not "no habits yet".
                assertEquals(HabitListDisplayState.NothingAtThisTime, awaitItem().displayState)
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

    @Test
    fun `swiping a row marks the day on screen as skipped`() =
        runTest {
            repository.seed(oldHabit(id = 1L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                assertFalse((awaitItem().displayState as Content).habits.single().isSkipped)

                viewModel.onHabitSkipToggled(habitId = 1L)

                assertTrue((awaitItem().displayState as Content).habits.single().isSkipped)
            }
        }

    @Test
    fun `skipping the same row again takes the skip back`() =
        runTest {
            repository.seed(oldHabit(id = 1L))
            val viewModel = subscribed()

            viewModel.onHabitSkipToggled(habitId = 1L)
            runCurrent()
            viewModel.onHabitSkipToggled(habitId = 1L)
            runCurrent()

            val row = (viewModel.uiState.value.displayState as Content).habits.single()
            assertFalse("Undo is the same gesture, so it must land back where it started", row.isSkipped)
        }

    @Test
    fun `skipping a completed day clears the tick`() =
        runTest {
            repository.seed(oldHabit(id = 1L))
            val viewModel = subscribed()
            viewModel.onHabitCheckedChanged(habitId = 1L)
            runCurrent()

            viewModel.onHabitSkipToggled(habitId = 1L)
            runCurrent()

            val row = (viewModel.uiState.value.displayState as Content).habits.single()
            assertTrue(row.isSkipped)
            assertFalse("A day cannot read as both done and skipped", row.isCompleted)
        }

    @Test
    fun `a skipped day does not break the streak shown on the row`() =
        runTest {
            repository.seed(oldHabit(id = 1L))
            // Kept on Sunday and Saturday, skipped on Friday, kept on Thursday. The skip is passed
            // over rather than ending the run, so all three kept days count as one streak.
            repository.completeOn(1L, monday.minusDays(1), monday.minusDays(2), monday.minusDays(4))
            repository.skipOn(1L, monday.minusDays(3))

            viewModel().uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                assertEquals(3, (awaitItem().displayState as Content).habits.single().currentStreak)
            }
        }

    @Test
    fun `a skip lands on the day being viewed, not on today`() =
        runTest {
            repository.seed(oldHabit(id = 1L))
            val viewModel = subscribed()
            viewModel.onPreviousDayClicked()
            runCurrent()

            viewModel.onHabitSkipToggled(habitId = 1L)
            runCurrent()

            assertEquals(listOf(monday.minusDays(1)), repository.skipsFor(1L))
        }

    @Test
    fun `the list is grouped into in progress, done and skipped, in that order`() =
        runTest {
            repository.seed(
                oldHabit(id = 1L, title = "Still to do"),
                oldHabit(id = 2L, title = "Finished"),
                oldHabit(id = 3L, title = "Passed on"),
            )
            repository.completeOn(2L, monday)
            repository.skipOn(3L, monday)

            viewModel().uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                val rows = (awaitItem().displayState as Content).rows

                // A header is followed by its own habits, so the list reads as sections rather
                // than as a heading block with everything underneath it.
                assertEquals(
                    listOf(
                        HabitSection.IN_PROGRESS to null,
                        null to "Still to do",
                        HabitSection.DONE to null,
                        null to "Finished",
                        HabitSection.SKIPPED to null,
                        null to "Passed on",
                    ),
                    rows.map { row ->
                        when (row) {
                            is HabitListRow.Header -> row.section to null
                            is HabitListRow.Habit -> null to row.item.title
                        }
                    },
                )
                assertTrue(rows.filterIsInstance<HabitListRow.Header>().all { it.count == 1 })
            }
        }

    @Test
    fun `a section with nothing in it is left out entirely`() =
        runTest {
            // A header reading "Done (0)" is noise: the count is the whole point of the heading.
            repository.seed(oldHabit(id = 1L, title = "Still to do"))

            viewModel().uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                val rows = (awaitItem().displayState as Content).rows

                assertEquals(
                    listOf(HabitSection.IN_PROGRESS),
                    rows.filterIsInstance<HabitListRow.Header>().map { it.section },
                )
            }
        }

    @Test
    fun `an amount habit with anything logged counts as done`() =
        runTest {
            // The rule everywhere else: any amount at all completes the day, there is no target.
            repository.seed(oldHabit(id = 1L, title = "Read"))
            repository.setAmount(habitId = 1L, date = monday, amount = 1)

            viewModel().uiState.test {
                assertEquals(HabitListDisplayState.Loading, awaitItem().displayState)
                val rows = (awaitItem().displayState as Content).rows

                assertEquals(
                    listOf(HabitSection.DONE),
                    rows.filterIsInstance<HabitListRow.Header>().map { it.section },
                )
            }
        }

    @Test
    fun `ticking a habit moves it from in progress to done`() =
        runTest {
            repository.seed(oldHabit(id = 1L, title = "Brush teeth"))
            val viewModel = subscribed()

            viewModel.onHabitCheckedChanged(habitId = 1L)
            runCurrent()

            val rows = (viewModel.uiState.value.displayState as Content).rows
            assertEquals(
                listOf(HabitSection.DONE),
                rows.filterIsInstance<HabitListRow.Header>().map { it.section },
            )
        }

    @Test
    fun `the date strip runs from the oldest habit to today, ending on today`() =
        runTest {
            // Scrolling has to reach every day the app has history for, or a date older than the
            // strip would be unreachable now the arrows are gone.
            val createdOn = monday.minusDays(3)
            repository.seed(
                habit(id = 1L, createdAt = createdOn.toEpochMilli()),
                habit(id = 2L, createdAt = monday.minusDays(1).toEpochMilli()),
            )

            viewModel().uiState.test {
                awaitItem()
                val days = awaitItem().days

                assertEquals(createdOn, days.first().date)
                assertEquals(monday, days.last().date)
                assertEquals(4, days.size)
            }
        }

    @Test
    fun `the strip marks today and the selected day apart`() =
        runTest {
            repository.seed(habit(id = 1L, createdAt = monday.minusDays(2).toEpochMilli()))
            val viewModel = subscribed()

            viewModel.onPreviousDayClicked()
            runCurrent()

            val days = viewModel.uiState.value.days
            assertEquals(monday.minusDays(1), days.single { it.isSelected }.date)
            assertEquals(monday, days.single { it.isToday }.date)
        }

    @Test
    fun `an empty app still offers today on the strip`() =
        runTest {
            // Nothing to scroll through yet, but the strip must not be an empty row.
            viewModel().uiState.test {
                awaitItem()
                val days = awaitItem().days

                assertEquals(listOf(monday), days.map { it.date })
                assertTrue(days.single().isToday)
            }
        }
}
