package com.example.dailyworktracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.ui.common.DayStatus
import com.example.dailyworktracker.util.DateProvider
import com.example.dailyworktracker.util.HabitMonth
import com.example.dailyworktracker.util.HabitVisibility
import com.example.dailyworktracker.util.ProgressSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * How every habit is going over one month.
 *
 * The arithmetic all lives in [ProgressSummary]; this only decides which month is on screen and
 * turns stored rows into the shapes the view draws.
 */
@HiltViewModel
class ProgressViewModel
    @Inject
    constructor(
        repository: HabitRepository,
        private val dateProvider: DateProvider,
    ) : ViewModel() {
        private val month = MutableStateFlow(YearMonth.from(dateProvider.today()))
        private val mode = MutableStateFlow(ProgressMode.SUMMARY)

        val uiState: StateFlow<ProgressScreenState> =
            combine(
                repository.observeAllHabits(),
                repository.observeAllCompletionDates(),
                repository.observeAllSkipDates(),
                month,
                mode,
            ) { habits, completions, skips, shownMonth, shownMode ->
                val today = dateProvider.today()
                // Progress is about what is being kept now, so an archived habit is not judged.
                val active = habits.filterNot { it.isArchived }
                val months = active.map { it.toHabitMonth(completions, skips) }

                ProgressScreenState(
                    month = shownMonth,
                    today = today,
                    mode = shownMode,
                    displayState =
                        if (active.isEmpty()) {
                            ProgressDisplayState.Empty
                        } else {
                            ProgressDisplayState.Content(
                                rate = ProgressSummary.completionRate(months, shownMonth, today),
                                days = calendarDays(months, shownMonth, today),
                                habits =
                                    active.zip(months) { habit, habitMonth ->
                                        ProgressHabitUiModel(
                                            id = habit.id,
                                            title = habit.title,
                                            emoji = habit.emoji,
                                            rate = ProgressSummary.rateFor(habitMonth, shownMonth, today),
                                        )
                                    },
                            )
                        },
                )
            }
                .catch { throwable ->
                    emit(
                        ProgressScreenState(
                            month = month.value,
                            today = dateProvider.today(),
                            mode = mode.value,
                            displayState = ProgressDisplayState.Error(throwable),
                        ),
                    )
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue =
                        ProgressScreenState(
                            month = YearMonth.from(dateProvider.today()),
                            today = dateProvider.today(),
                            mode = ProgressMode.SUMMARY,
                            displayState = ProgressDisplayState.Loading,
                        ),
                )

        fun onModeSelected(selected: ProgressMode) {
            mode.value = selected
        }

        fun onPreviousMonthClicked() {
            month.value = month.value.minusMonths(1)
        }

        /**
         * Guarded as well as hidden.
         *
         * The button is disabled from `canGoToNextMonth`, but the guard is what makes a future
         * month impossible rather than merely hard to reach.
         */
        fun onNextMonthClicked() {
            val next = month.value.plusMonths(1)
            if (!next.isAfter(YearMonth.from(dateProvider.today()))) month.value = next
        }

        private fun Habit.toHabitMonth(
            completions: Map<Long, Set<LocalDate>>,
            skips: Map<Long, Set<LocalDate>>,
        ) = HabitMonth(
            habitId = id,
            scheduleDaysBitmask = scheduleDaysBitmask,
            createdOn = HabitVisibility.createdDate(this),
            completed = completions[id].orEmpty(),
            skipped = skips[id].orEmpty(),
        )

        /**
         * The month as a seven-column grid.
         *
         * Leading blanks are real items so every date lands under its own weekday. Trailing blanks
         * are not needed: the grid simply ends.
         */
        private fun calendarDays(
            habits: List<HabitMonth>,
            shownMonth: YearMonth,
            today: LocalDate,
        ): List<CalendarDay> {
            val leadingBlanks = shownMonth.atDay(1).dayOfWeek.value - 1

            return List(leadingBlanks) { CalendarDay(date = null, status = DayStatus.OUT_OF_RANGE) } +
                (1..shownMonth.lengthOfMonth()).map { dayOfMonth ->
                    val date = shownMonth.atDay(dayOfMonth)
                    CalendarDay(date, ProgressSummary.dayStatus(habits, date, today))
                }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
