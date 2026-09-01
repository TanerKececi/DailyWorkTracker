package com.example.dailyworktracker.ui.addedithabit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyworktracker.R
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.model.HabitGoal
import com.example.dailyworktracker.data.model.HabitUnit
import com.example.dailyworktracker.data.model.toGoal
import com.example.dailyworktracker.data.model.withGoal
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.util.WeekdaySchedule
import com.example.dailyworktracker.util.reminderTime
import com.example.dailyworktracker.util.withReminderTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

/**
 * Backs the add/edit sheet.
 *
 * The habit id arrives through [SavedStateHandle] rather than a constructor argument passed by the
 * Fragment, so the ViewModel survives process death without the Fragment re-supplying it.
 */
@HiltViewModel
class AddEditHabitViewModel
    @Inject
    constructor(
        private val repository: HabitRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val habitId: Long = savedStateHandle.get<Long>(ARG_HABIT_ID) ?: NEW_HABIT_ID

        private val _uiState =
            MutableStateFlow(
                AddEditHabitScreenState(
                    isEditing = habitId != NEW_HABIT_ID,
                    scheduleDaysBitmask = WeekdaySchedule.EVERY_DAY,
                ),
            )
        val uiState: StateFlow<AddEditHabitScreenState> = _uiState.asStateFlow()

        /** Set when editing, so saving updates the original row instead of creating a duplicate. */
        private var editedHabit: Habit? = null

        init {
            if (habitId != NEW_HABIT_ID) {
                loadHabit(habitId)
            }
        }

        private fun loadHabit(id: Long) {
            viewModelScope.launch {
                val habit = repository.getHabit(id) ?: return@launch
                editedHabit = habit
                _uiState.update {
                    it.copy(
                        title = habit.title,
                        emoji = habit.emoji,
                        scheduleDaysBitmask = habit.scheduleDaysBitmask,
                        isReminderEnabled = habit.reminderTime != null,
                        reminderTime = habit.reminderTime ?: it.reminderTime,
                        isAmountTracked = habit.toGoal() is HabitGoal.Amount,
                        unit = (habit.toGoal() as? HabitGoal.Amount)?.unit ?: it.unit,
                    )
                }
            }
        }

        fun onTitleChanged(title: String) {
            _uiState.update {
                it.copy(title = title, displayState = it.editingWithoutTitleError())
            }
        }

        fun onEmojiChanged(emoji: String) {
            _uiState.update { it.copy(emoji = emoji) }
        }

        fun onAmountTrackedChanged(isTracked: Boolean) {
            _uiState.update { it.copy(isAmountTracked = isTracked) }
        }

        fun onUnitChanged(unit: HabitUnit) {
            _uiState.update { it.copy(unit = unit) }
        }

        fun onDayToggled(
            day: DayOfWeek,
            isScheduled: Boolean,
        ) {
            _uiState.update {
                it.copy(
                    scheduleDaysBitmask =
                        WeekdaySchedule.withDay(
                            it.scheduleDaysBitmask,
                            day,
                            isScheduled,
                        ),
                    displayState = it.editingWithoutScheduleError(),
                )
            }
        }

        fun onEveryDayToggled(isEveryDay: Boolean) {
            _uiState.update {
                it.copy(
                    scheduleDaysBitmask =
                        if (isEveryDay) WeekdaySchedule.EVERY_DAY else WeekdaySchedule.NONE,
                    displayState = it.editingWithoutScheduleError(),
                )
            }
        }

        fun onReminderEnabledChanged(isEnabled: Boolean) {
            _uiState.update { it.copy(isReminderEnabled = isEnabled) }
        }

        fun onReminderTimeChanged(time: LocalTime) {
            _uiState.update { it.copy(reminderTime = time) }
        }

        fun onSaveClicked() {
            val state = _uiState.value
            val trimmedTitle = state.title.trim()

            val titleError = R.string.add_habit_error_empty_title.takeIf { trimmedTitle.isEmpty() }
            val scheduleError =
                R.string.add_habit_error_no_days
                    .takeIf { !WeekdaySchedule.hasAnyDay(state.scheduleDaysBitmask) }

            if (titleError != null || scheduleError != null) {
                _uiState.update {
                    it.copy(
                        displayState =
                            AddEditHabitDisplayState.Editing(
                                titleError = titleError,
                                scheduleError = scheduleError,
                            ),
                    )
                }
                return
            }

            val emoji = state.emoji.trim().ifEmpty { AddEditHabitScreenState.DEFAULT_EMOJI }

            viewModelScope.launch {
                _uiState.update { it.copy(displayState = AddEditHabitDisplayState.Saving) }
                val existing = editedHabit
                if (existing != null) {
                    repository.updateHabit(
                        existing.copy(
                            title = trimmedTitle,
                            emoji = emoji,
                            scheduleDaysBitmask = state.scheduleDaysBitmask,
                        ).withGoal(state.goal)
                            .withReminderTime(state.reminderTime.takeIf { state.isReminderEnabled }),
                    )
                } else {
                    repository.addHabit(
                        Habit(
                            title = trimmedTitle,
                            emoji = emoji,
                            scheduleDaysBitmask = state.scheduleDaysBitmask,
                            createdAt = System.currentTimeMillis(),
                        ).withGoal(state.goal)
                            .withReminderTime(state.reminderTime.takeIf { state.isReminderEnabled }),
                    )
                }
                _uiState.update { it.copy(displayState = AddEditHabitDisplayState.Saved) }
            }
        }

        /**
         * Clears one field's validation error while keeping the other's.
         *
         * Editing any field also returns the sheet to [AddEditHabitDisplayState.Editing], which is
         * the only state the errors belong to.
         */
        private fun AddEditHabitScreenState.editingWithoutTitleError() =
            AddEditHabitDisplayState.Editing(titleError = null, scheduleError = scheduleError)

        private fun AddEditHabitScreenState.editingWithoutScheduleError() =
            AddEditHabitDisplayState.Editing(titleError = titleError, scheduleError = null)

        companion object {
            const val ARG_HABIT_ID = "habitId"

            /** Sentinel for "creating a new habit", matching the nav argument's default. */
            const val NEW_HABIT_ID = -1L
        }
    }
