package com.example.dailyworktracker.ui.habitlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyworktracker.R
import com.example.dailyworktracker.data.model.TodayHabit
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.ui.common.UiState
import com.example.dailyworktracker.util.DateProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitListViewModel
    @Inject
    constructor(
        private val repository: HabitRepository,
        private val dateProvider: DateProvider,
    ) : ViewModel() {
        private val _selectedDate = MutableStateFlow(dateProvider.today())

        /** The day being viewed and edited. Every other piece of screen state derives from it. */
        val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

        /**
         * Whether the selection means "whatever today is" rather than one specific day.
         *
         * Needed to fix midnight rollover: once the day turns over, a selection of yesterday's date
         * is indistinguishable from a deliberately chosen yesterday unless the intent is recorded.
         */
        private var isFollowingToday = true

        val uiState: StateFlow<UiState<List<HabitListItemUiModel>>> =
            _selectedDate
                .flatMapLatest { date ->
                    combine(
                        repository.observeHabitsFor(date),
                        repository.observeActiveHabitCount(),
                    ) { habits, activeHabitCount ->
                        when {
                            habits.isNotEmpty() -> UiState.Success(habits.map(TodayHabit::toUiModel))

                            // Habits exist, none is due this day: "no habits yet" would be wrong.
                            activeHabitCount > 0 ->
                                UiState.Empty(
                                    titleRes = R.string.habit_list_nothing_scheduled_title,
                                    messageRes = R.string.habit_list_nothing_scheduled_message,
                                )

                            else ->
                                UiState.Empty(
                                    titleRes = R.string.habit_list_empty_title,
                                    messageRes = R.string.habit_list_empty_message,
                                )
                        }
                    }
                }
                .catch { emit(UiState.Error(it)) }
                .stateIn(
                    scope = viewModelScope,
                    // Keep the DB subscription briefly across config changes instead of re-querying.
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading,
                )

        /** Exposed so the view can label dates relative to it without a clock of its own. */
        fun today(): LocalDate = dateProvider.today()

        /** Future days cannot be completed, so stepping forward stops at today. */
        fun canGoToNextDay(): Boolean = _selectedDate.value.isBefore(dateProvider.today())

        fun onPreviousDayClicked() {
            select(_selectedDate.value.minusDays(1))
        }

        fun onNextDayClicked() {
            if (canGoToNextDay()) select(_selectedDate.value.plusDays(1))
        }

        fun onTodayClicked() {
            select(dateProvider.today())
        }

        fun onDatePicked(date: LocalDate) {
            // Guard the picker as well as the chevron: neither may select the future.
            select(minOf(date, dateProvider.today()))
        }

        /**
         * Re-resolves today when the screen comes back to the foreground.
         *
         * An app left open past midnight would otherwise keep showing the previous day. Only a
         * selection that still means "today" moves, so a deliberately chosen past date stays put.
         */
        fun onScreenResumed() {
            if (isFollowingToday) {
                _selectedDate.value = dateProvider.today()
            }
        }

        private fun select(date: LocalDate) {
            isFollowingToday = date == dateProvider.today()
            _selectedDate.value = date
        }

        fun onHabitCheckedChanged(habitId: Long) {
            val date = _selectedDate.value
            viewModelScope.launch { repository.toggleCompletion(habitId, date) }
        }

        fun onHabitArchived(habitId: Long) {
            viewModelScope.launch { repository.archiveHabit(habitId) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun TodayHabit.toUiModel() =
    HabitListItemUiModel(
        id = habit.id,
        title = habit.title,
        emoji = habit.emoji,
        isCompleted = isCompleted,
        scheduleDaysBitmask = habit.scheduleDaysBitmask,
        currentStreak = currentStreak,
    )
