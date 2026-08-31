package com.example.dailyworktracker.ui.addedithabit

import androidx.lifecycle.SavedStateHandle
import com.example.dailyworktracker.R
import com.example.dailyworktracker.fake.FakeHabitRepository
import com.example.dailyworktracker.fake.habit
import com.example.dailyworktracker.testing.MainDispatcherRule
import com.example.dailyworktracker.util.WeekdaySchedule
import com.example.dailyworktracker.util.reminderTime
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class AddEditHabitViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeHabitRepository()

    private fun viewModel(habitId: Long? = null) =
        AddEditHabitViewModel(
            repository = repository,
            savedStateHandle =
                SavedStateHandle(
                    habitId?.let { mapOf(AddEditHabitViewModel.ARG_HABIT_ID to it) } ?: emptyMap(),
                ),
        )

    @Test
    fun `new habit defaults to every day`() {
        val state = viewModel().uiState.value

        assertFalse(state.isEditing)
        assertEquals(WeekdaySchedule.EVERY_DAY, state.scheduleDaysBitmask)
    }

    @Test
    fun `rejects a blank title`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onTitleChanged("   ")
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(R.string.add_habit_error_empty_title, viewModel.uiState.value.titleError)
            assertFalse(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `rejects a schedule with no days`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onTitleChanged("Do sport")
            viewModel.onEveryDayToggled(false)
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(R.string.add_habit_error_no_days, viewModel.uiState.value.scheduleError)
            assertFalse(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `reports both validation errors at once`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEveryDayToggled(false)
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(R.string.add_habit_error_empty_title, state.titleError)
            assertEquals(R.string.add_habit_error_no_days, state.scheduleError)
        }

    @Test
    fun `editing the title clears its error`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onSaveClicked()
            advanceUntilIdle()
            assertEquals(R.string.add_habit_error_empty_title, viewModel.uiState.value.titleError)

            viewModel.onTitleChanged("Do sport")

            assertNull(viewModel.uiState.value.titleError)
        }

    @Test
    fun `saves a new habit with a trimmed title`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onTitleChanged("  Do sport  ")
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isSaved)
            assertEquals("Do sport", repository.getHabit(1L)?.title)
        }

    @Test
    fun `falls back to the default emoji when none is given`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onTitleChanged("Do sport")
            viewModel.onEmojiChanged("  ")
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(AddEditHabitUiState.DEFAULT_EMOJI, repository.getHabit(1L)?.emoji)
        }

    @Test
    fun `toggling a day off narrows the schedule`() {
        val viewModel = viewModel()

        viewModel.onDayToggled(DayOfWeek.MONDAY, isScheduled = false)

        val mask = viewModel.uiState.value.scheduleDaysBitmask
        assertFalse(WeekdaySchedule.isScheduledOn(mask, DayOfWeek.MONDAY))
        assertTrue(WeekdaySchedule.isScheduledOn(mask, DayOfWeek.TUESDAY))
    }

    @Test
    fun `every day toggle selects and clears all days`() {
        val viewModel = viewModel()

        viewModel.onEveryDayToggled(false)
        assertEquals(WeekdaySchedule.NONE, viewModel.uiState.value.scheduleDaysBitmask)

        viewModel.onEveryDayToggled(true)
        assertEquals(WeekdaySchedule.EVERY_DAY, viewModel.uiState.value.scheduleDaysBitmask)
    }

    @Test
    fun `loads an existing habit for editing`() =
        runTest {
            val mask = WeekdaySchedule.toBitmask(listOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
            repository.seed(habit(id = 7L, title = "Wash dishes", emoji = "🍽", scheduleDaysBitmask = mask))

            val viewModel = viewModel(habitId = 7L)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.isEditing)
            assertEquals("Wash dishes", state.title)
            assertEquals("🍽", state.emoji)
            assertEquals(mask, state.scheduleDaysBitmask)
        }

    @Test
    fun `saving an edit updates in place instead of creating a duplicate`() =
        runTest {
            repository.seed(habit(id = 7L, title = "Wash dishes"))

            val viewModel = viewModel(habitId = 7L)
            advanceUntilIdle()
            viewModel.onTitleChanged("Wash the dishes")
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals("Wash the dishes", repository.getHabit(7L)?.title)
            assertEquals(1, repository.habitCount())
        }

    @Test
    fun `editing preserves fields the form does not expose`() =
        runTest {
            // createdAt has no input, so a careless save would reset it.
            repository.seed(habit(id = 7L, createdAt = 12345L))

            val viewModel = viewModel(habitId = 7L)
            advanceUntilIdle()
            viewModel.onTitleChanged("Renamed")
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(12345L, repository.getHabit(7L)?.createdAt)
        }

    @Test
    fun `a new habit starts with the reminder switched off`() {
        val state = viewModel().uiState.value

        assertFalse(state.isReminderEnabled)
        assertEquals(AddEditHabitUiState.DEFAULT_REMINDER_TIME, state.reminderTime)
    }

    @Test
    fun `switching the reminder off and on again keeps the chosen time`() {
        val viewModel = viewModel()

        viewModel.onReminderEnabledChanged(true)
        viewModel.onReminderTimeChanged(LocalTime.of(7, 30))
        viewModel.onReminderEnabledChanged(false)
        viewModel.onReminderEnabledChanged(true)

        assertEquals(LocalTime.of(7, 30), viewModel.uiState.value.reminderTime)
    }

    @Test
    fun `saves the chosen reminder time`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onTitleChanged("Do sport")
            viewModel.onReminderEnabledChanged(true)
            viewModel.onReminderTimeChanged(LocalTime.of(18, 45))
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(LocalTime.of(18, 45), repository.getHabit(1L)?.reminderTime)
        }

    @Test
    fun `a time picked while the reminder is off is not saved`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onTitleChanged("Do sport")
            viewModel.onReminderTimeChanged(LocalTime.of(18, 45))
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val saved = requireNotNull(repository.getHabit(1L))
            assertNull(saved.reminderTime)
            // Both halves must be absent: an hour without a minute is not a time.
            assertNull(saved.reminderHour)
            assertNull(saved.reminderMinute)
        }

    @Test
    fun `editing loads the existing reminder`() =
        runTest {
            repository.seed(habit(id = 7L, reminderTime = LocalTime.of(6, 15)))

            val viewModel = viewModel(habitId = 7L)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.isReminderEnabled)
            assertEquals(LocalTime.of(6, 15), state.reminderTime)
        }

    @Test
    fun `editing a habit without a reminder leaves the switch off`() =
        runTest {
            repository.seed(habit(id = 7L))

            val viewModel = viewModel(habitId = 7L)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isReminderEnabled)
        }

    @Test
    fun `turning a reminder off while editing removes it from the saved habit`() =
        runTest {
            repository.seed(habit(id = 7L, reminderTime = LocalTime.of(6, 15)))

            val viewModel = viewModel(habitId = 7L)
            advanceUntilIdle()
            viewModel.onReminderEnabledChanged(false)
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val saved = requireNotNull(repository.getHabit(7L))
            assertNull(saved.reminderTime)
            assertNull(saved.reminderHour)
            assertNull(saved.reminderMinute)
        }

    @Test
    fun `editing preserves a reminder the user did not touch`() =
        runTest {
            repository.seed(habit(id = 7L, reminderTime = LocalTime.of(6, 15)))

            val viewModel = viewModel(habitId = 7L)
            advanceUntilIdle()
            viewModel.onTitleChanged("Renamed")
            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(LocalTime.of(6, 15), repository.getHabit(7L)?.reminderTime)
        }
}
