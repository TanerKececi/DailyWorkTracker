package com.example.dailyworktracker.ui.allhabits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.util.reminderTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the manage-habits screen.
 *
 * The today list is filtered by schedule, which leaves habits that do not repeat today unreachable.
 * This screen is the way back to them: it lists every habit, archived ones included.
 */
@HiltViewModel
class AllHabitsViewModel
    @Inject
    constructor(
        private val repository: HabitRepository,
    ) : ViewModel() {
        val uiState: StateFlow<AllHabitsDisplayState> =
            repository.observeAllHabits()
                .map { habits ->
                    if (habits.isEmpty()) {
                        AllHabitsDisplayState.Empty
                    } else {
                        AllHabitsDisplayState.Content(habits.map(Habit::toUiModel))
                    }
                }
                .catch { emit(AllHabitsDisplayState.Error(it)) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = AllHabitsDisplayState.Loading,
                )

        fun onArchiveToggled(
            habitId: Long,
            isCurrentlyArchived: Boolean,
        ) {
            viewModelScope.launch {
                if (isCurrentlyArchived) {
                    repository.unarchiveHabit(habitId)
                } else {
                    repository.archiveHabit(habitId)
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun Habit.toUiModel() =
    AllHabitItemUiModel(
        id = id,
        title = title,
        emoji = emoji,
        scheduleDaysBitmask = scheduleDaysBitmask,
        reminderTime = reminderTime,
        isArchived = isArchived,
    )
