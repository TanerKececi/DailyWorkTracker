package com.example.dailyworktracker.ui.habitlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyworktracker.data.model.TodayHabit
import com.example.dailyworktracker.data.model.toGoal
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.util.DateProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
        /** The day being viewed and edited. Every other piece of screen state derives from it. */
        private val selectedDate = MutableStateFlow(dateProvider.today())

        /**
         * Today, re-read whenever the screen resumes.
         *
         * Held as state rather than called on demand so the date bar cannot disagree with the list:
         * both are now decided in one place, at one moment.
         */
        private val today = MutableStateFlow(dateProvider.today())

        /**
         * Whether the selection means "whatever today is" rather than one specific day.
         *
         * Needed to fix midnight rollover: once the day turns over, a selection of yesterday's date
         * is indistinguishable from a deliberately chosen yesterday unless the intent is recorded.
         */
        private var isFollowingToday = true

        val uiState: StateFlow<HabitListScreenState> =
            selectedDate
                .flatMapLatest { date ->
                    combine(
                        repository.observeHabitsFor(date),
                        repository.observeActiveHabitCount(),
                        today,
                    ) { habits, activeHabitCount, currentToday ->
                        HabitListScreenState(
                            selectedDate = date,
                            today = currentToday,
                            displayState =
                                when {
                                    habits.isNotEmpty() ->
                                        HabitListDisplayState.Content(habits.map(TodayHabit::toUiModel))

                                    // Habits exist, none is due this day: "no habits yet" would be wrong.
                                    activeHabitCount > 0 -> HabitListDisplayState.NothingScheduled

                                    else -> HabitListDisplayState.NoHabitsYet
                                },
                        )
                    }
                }
                .catch { emit(screenState(HabitListDisplayState.Error(it))) }
                .stateIn(
                    scope = viewModelScope,
                    // Keep the DB subscription briefly across config changes instead of re-querying.
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = screenState(HabitListDisplayState.Loading),
                )

        /** The date bar renders in every state, so even Loading and Error carry the current dates. */
        private fun screenState(displayState: HabitListDisplayState) =
            HabitListScreenState(
                selectedDate = selectedDate.value,
                today = today.value,
                displayState = displayState,
            )

        fun onPreviousDayClicked() {
            select(selectedDate.value.minusDays(1))
        }

        fun onNextDayClicked() {
            // Guarded here as well as in the view: the future is never completable.
            if (selectedDate.value.isBefore(dateProvider.today())) {
                select(selectedDate.value.plusDays(1))
            }
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
         * selection that still means "today" moves, so a deliberately chosen past date stays put -
         * but [today] always advances, so the date bar stays right either way.
         */
        fun onScreenResumed() {
            val resumedToday = dateProvider.today()
            today.value = resumedToday
            if (isFollowingToday) {
                selectedDate.value = resumedToday
            }
        }

        private fun select(date: LocalDate) {
            isFollowingToday = date == dateProvider.today()
            selectedDate.value = date
        }

        fun onHabitCheckedChanged(habitId: Long) {
            val date = selectedDate.value
            viewModelScope.launch { repository.toggleCompletion(habitId, date) }
        }

        /**
         * Records what was done on the day being viewed, for a habit logged as a number.
         *
         * Takes the date from the selection for the same reason [onHabitCheckedChanged] does:
         * backfilling must land on the day on screen, never on today.
         */
        fun onAmountEntered(
            habitId: Long,
            amount: Int,
        ) {
            val date = selectedDate.value
            viewModelScope.launch { repository.setAmount(habitId, date, amount) }
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
        goal = habit.toGoal(),
        amount = amount,
    )
