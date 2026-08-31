package com.example.dailyworktracker.ui.habitdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyworktracker.R
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.ui.common.UiState
import com.example.dailyworktracker.util.DateProvider
import com.example.dailyworktracker.util.HabitStatistics
import com.example.dailyworktracker.util.HabitVisibility
import com.example.dailyworktracker.util.StreakCalculator
import com.example.dailyworktracker.util.WeekdaySchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/** Shows one habit's history: streaks, completion rate, and a grid of recent days. */
@HiltViewModel
class HabitDetailViewModel
    @Inject
    constructor(
        repository: HabitRepository,
        private val dateProvider: DateProvider,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val habitId: Long = requireNotNull(savedStateHandle[ARG_HABIT_ID])

        val uiState: StateFlow<UiState<HabitDetailUiState>> =
            combine(
                repository.observeHabit(habitId),
                repository.observeCompletionDates(habitId),
            ) { habit, completionDates ->
                // Archiving or deleting the habit elsewhere leaves this screen with nothing to show.
                if (habit == null) {
                    UiState.Empty(
                        titleRes = R.string.habit_detail_missing_title,
                        messageRes = R.string.habit_detail_missing_message,
                    )
                } else {
                    UiState.Success(buildState(habit, completionDates.toSet()))
                }
            }
                .catch { emit(UiState.Error(it)) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading,
                )

        private fun buildState(
            habit: Habit,
            completed: Set<LocalDate>,
        ): HabitDetailUiState {
            val today = dateProvider.today()
            val createdOn = HabitVisibility.createdDate(habit)

            return HabitDetailUiState(
                title = habit.title,
                emoji = habit.emoji,
                scheduleDaysBitmask = habit.scheduleDaysBitmask,
                currentStreak =
                    StreakCalculator.currentStreak(completed, habit.scheduleDaysBitmask, today),
                longestStreak =
                    StreakCalculator.longestStreak(completed, habit.scheduleDaysBitmask, today),
                completionRate =
                    HabitStatistics.completionRate(
                        completedDates = completed,
                        scheduleDaysBitmask = habit.scheduleDaysBitmask,
                        from = createdOn,
                        to = today,
                    ),
                completedCount = completed.size,
                heatmap = buildHeatmap(habit, completed, createdOn, today),
            )
        }

        /**
         * Whole weeks ending on the week containing today, so every row is one week and the weekday
         * header lines up.
         *
         * The grid starts at the habit's first week rather than always [WEEKS_SHOWN] back: a habit
         * created today would otherwise open on three months of blank rows. Days inside the range
         * that predate the habit are kept as empty slots, since dropping them would shift the
         * remaining days into the wrong weekday columns.
         */
        private fun buildHeatmap(
            habit: Habit,
            completed: Set<LocalDate>,
            createdOn: LocalDate,
            today: LocalDate,
        ): List<HeatmapCellUiModel> {
            val lastDay = today.with(DayOfWeek.SUNDAY)
            val earliestShown = lastDay.minusWeeks(WEEKS_SHOWN - 1L).with(DayOfWeek.MONDAY)
            val firstDay = maxOf(earliestShown, createdOn.with(DayOfWeek.MONDAY))

            return generateSequence(firstDay) { it.plusDays(1) }
                .takeWhile { !it.isAfter(lastDay) }
                .map { date ->
                    HeatmapCellUiModel(date = date, status = statusOf(habit, completed, date, createdOn, today))
                }.toList()
        }

        private fun statusOf(
            habit: Habit,
            completed: Set<LocalDate>,
            date: LocalDate,
            createdOn: LocalDate,
            today: LocalDate,
        ): DayStatus =
            when {
                date.isAfter(today) || date.isBefore(createdOn) -> DayStatus.OUT_OF_RANGE
                !WeekdaySchedule.isScheduledOn(habit.scheduleDaysBitmask, date.dayOfWeek) ->
                    DayStatus.NOT_SCHEDULED

                date in completed -> DayStatus.COMPLETED
                // Today has not resolved yet, so it is neither a miss nor an off-day.
                date == today -> DayStatus.PENDING
                else -> DayStatus.MISSED
            }

        companion object {
            const val ARG_HABIT_ID = "habitId"
            const val WEEKS_SHOWN = 12

            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
