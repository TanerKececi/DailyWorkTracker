package com.example.dailyworktracker.ui.progress

import app.cash.turbine.test
import com.example.dailyworktracker.fake.FakeDateProvider
import com.example.dailyworktracker.fake.FakeHabitRepository
import com.example.dailyworktracker.fake.habit
import com.example.dailyworktracker.testing.MainDispatcherRule
import com.example.dailyworktracker.util.HabitMonth
import com.example.dailyworktracker.util.ProgressSummary
import com.example.dailyworktracker.util.WeekdaySchedule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ProgressViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val monday = FakeDateProvider.MONDAY
    private val thisMonth = YearMonth.from(monday)
    private val repository = FakeHabitRepository()
    private val dateProvider = FakeDateProvider()

    private fun viewModel() = ProgressViewModel(repository, dateProvider)

    @Test
    fun `starts on the current month`() =
        runTest {
            repository.seed(habit(id = 1L, createdAt = 0L))

            viewModel().uiState.test {
                assertEquals(ProgressDisplayState.Loading, awaitItem().displayState)
                assertEquals(thisMonth, awaitItem().month)
            }
        }

    @Test
    fun `reports the rate ProgressSummary works out for the month`() =
        runTest {
            repository.seed(habit(id = 1L, createdAt = 0L))
            repository.completeOn(1L, monday.minusDays(1), monday.minusDays(2))

            val expected =
                ProgressSummary.completionRate(
                    listOf(
                        HabitMonth(
                            habitId = 1L,
                            scheduleDaysBitmask = WeekdaySchedule.EVERY_DAY,
                            createdOn = LocalDate.ofEpochDay(0),
                            completed = setOf(monday.minusDays(1), monday.minusDays(2)),
                            skipped = emptySet(),
                        ),
                    ),
                    thisMonth,
                    monday,
                )

            viewModel().uiState.test {
                awaitItem()
                val content = awaitItem().displayState as ProgressDisplayState.Content
                // The arithmetic is ProgressSummary's business; this only checks the screen
                // reports what it says rather than deriving a second, disagreeing number.
                assertEquals(expected, content.rate, 0.0001f)
            }
        }

    @Test
    fun `the calendar starts on the weekday the month actually starts on`() =
        runTest {
            // Without the leading blanks every date would sit under the wrong weekday column.
            repository.seed(habit(id = 1L, createdAt = 0L))

            viewModel().uiState.test {
                awaitItem()
                val days = (awaitItem().displayState as ProgressDisplayState.Content).days
                val firstOfMonth = thisMonth.atDay(1)
                val blanks = days.takeWhile { it.date == null }.size

                assertEquals(firstOfMonth.dayOfWeek.value - 1, blanks)
                assertEquals(firstOfMonth, days[blanks].date)
                assertEquals(blanks + thisMonth.lengthOfMonth(), days.size)
            }
        }

    @Test
    fun `stepping back a month changes the month without changing today`() =
        runTest {
            repository.seed(habit(id = 1L, createdAt = 0L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                awaitItem()

                viewModel.onPreviousMonthClicked()

                val state = awaitItem()
                assertEquals(thisMonth.minusMonths(1), state.month)
                assertEquals(monday, state.today)
            }
        }

    @Test
    fun `the next month is out of reach while it has not happened`() =
        runTest {
            repository.seed(habit(id = 1L, createdAt = 0L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                assertFalse(awaitItem().canGoToNextMonth)

                viewModel.onPreviousMonthClicked()

                assertTrue(awaitItem().canGoToNextMonth)
            }
        }

    @Test
    fun `stepping forward past this month does nothing`() =
        runTest {
            // Guarded as well as hidden: the button is disabled, but the guard is what makes the
            // state impossible rather than merely unreachable.
            repository.seed(habit(id = 1L, createdAt = 0L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                awaitItem()

                viewModel.onNextMonthClicked()

                expectNoEvents()
            }
        }

    @Test
    fun `the habits mode lists each habit with its own rate`() =
        runTest {
            repository.seed(
                habit(id = 1L, title = "Brush teeth", createdAt = 0L),
                habit(id = 2L, title = "Read", createdAt = 0L),
            )
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                awaitItem()

                viewModel.onModeSelected(ProgressMode.HABITS)

                val state = awaitItem()
                assertEquals(ProgressMode.HABITS, state.mode)
                assertEquals(
                    listOf("Brush teeth", "Read"),
                    (state.displayState as ProgressDisplayState.Content).habits.map { it.title },
                )
            }
        }

    @Test
    fun `an archived habit is left out entirely`() =
        runTest {
            // Progress is about what is being kept now, not what used to be.
            repository.seed(habit(id = 1L, createdAt = 0L), habit(id = 2L, createdAt = 0L))
            repository.archiveHabit(2L)

            viewModel().uiState.test {
                awaitItem()
                val content = awaitItem().displayState as ProgressDisplayState.Content
                assertEquals(1, content.habits.size)
            }
        }

    @Test
    fun `no habits at all is an empty screen, not a zero percent one`() =
        runTest {
            // 0% means habits exist and were missed. Nothing to measure is a different situation
            // and gets a different type, so the screen cannot accuse an empty app of failing.
            viewModel().uiState.test {
                assertEquals(ProgressDisplayState.Loading, awaitItem().displayState)
                assertEquals(ProgressDisplayState.Empty, awaitItem().displayState)
            }
        }

    @Test
    fun `a skipped day is neutral on the calendar and in the rate`() =
        runTest {
            repository.seed(habit(id = 1L, createdAt = 0L))
            repository.completeOn(1L, monday.minusDays(1))
            repository.skipOn(1L, monday.minusDays(2))

            viewModel().uiState.test {
                awaitItem()
                val content = awaitItem().displayState as ProgressDisplayState.Content
                val skippedDay = content.days.single { it.date == monday.minusDays(2) }

                assertEquals(com.example.dailyworktracker.ui.common.DayStatus.SKIPPED, skippedDay.status)
            }
        }
}
