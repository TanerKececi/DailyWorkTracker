package com.example.dailyworktracker.ui.habitlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyworktracker.R
import com.example.dailyworktracker.data.model.HabitWithStatus
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HabitListViewModel
    @Inject
    constructor(
        private val repository: HabitRepository,
    ) : ViewModel() {
        val uiState: StateFlow<UiState<List<HabitListItemUiModel>>> =
            combine(
                repository.observeTodaysHabits(),
                repository.observeActiveHabitCount(),
            ) { todaysHabits, activeHabitCount ->
                when {
                    todaysHabits.isNotEmpty() ->
                        UiState.Success(todaysHabits.map(HabitWithStatus::toUiModel))

                    // Habits exist, none is due today: saying "no habits yet" would be wrong.
                    activeHabitCount > 0 ->
                        UiState.Empty(
                            titleRes = R.string.habit_list_nothing_today_title,
                            messageRes = R.string.habit_list_nothing_today_message,
                        )

                    else ->
                        UiState.Empty(
                            titleRes = R.string.habit_list_empty_title,
                            messageRes = R.string.habit_list_empty_message,
                        )
                }
            }
                .catch { emit(UiState.Error(it)) }
                .stateIn(
                    scope = viewModelScope,
                    // Keep the DB subscription briefly across config changes instead of re-querying.
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading,
                )

        fun onHabitCheckedChanged(habitId: Long) {
            viewModelScope.launch { repository.toggleCompletionToday(habitId) }
        }

        fun onHabitArchived(habitId: Long) {
            viewModelScope.launch { repository.archiveHabit(habitId) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun HabitWithStatus.toUiModel() =
    HabitListItemUiModel(
        id = habit.id,
        title = habit.title,
        emoji = habit.emoji,
        isCompleted = isCompleted,
        scheduleDaysBitmask = habit.scheduleDaysBitmask,
    )
