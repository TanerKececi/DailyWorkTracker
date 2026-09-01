package com.example.dailyworktracker.ui.habitdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.repository.HabitRepository
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
import java.time.YearMonth
import java.time.temporal.ChronoUnit
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

        val uiState: StateFlow<HabitDetailDisplayState> =
            combine(
                repository.observeHabit(habitId),
                repository.observeCompletionDates(habitId),
            ) { habit, completionDates ->
                // Archiving or deleting the habit elsewhere leaves this screen with nothing to show.
                if (habit == null) {
                    HabitDetailDisplayState.Missing
                } else {
                    HabitDetailDisplayState.Content(buildState(habit, completionDates.toSet()))
                }
            }
                .catch { emit(HabitDetailDisplayState.Error(it)) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = HabitDetailDisplayState.Loading,
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
         * Whole weeks ending on the week containing today, each row being a month gutter followed by
         * its seven days so the grid lines up with the weekday header.
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
        ): List<HeatmapItem> {
            val lastDay = today.with(DayOfWeek.SUNDAY)
            val earliestShown = lastDay.minusWeeks(WEEKS_SHOWN - 1L).with(DayOfWeek.MONDAY)
            val firstMonday = maxOf(earliestShown, createdOn.with(DayOfWeek.MONDAY))

            val items = mutableListOf<HeatmapItem>()
            var monday = firstMonday
            var previousMonth: YearMonth? = null
            val firstMonth = YearMonth.from(firstMonday)

            while (!monday.isAfter(lastDay)) {
                // A week takes the month of its Monday, for both its label and its band. Banding
                // per day instead would staircase mid-row and disagree with the heading beside it.
                val weekMonth = YearMonth.from(monday)
                val isAlternate = ChronoUnit.MONTHS.between(firstMonth, weekMonth) % 2L != 0L

                items +=
                    HeatmapItem.WeekGutter(
                        weekStart = monday,
                        // Label only when the month changes, so it reads as a heading.
                        month = weekMonth.takeIf { it != previousMonth },
                        isAlternateMonth = isAlternate,
                    )
                previousMonth = weekMonth

                (0L until DAYS_PER_WEEK).forEach { offset ->
                    val date = monday.plusDays(offset)
                    items +=
                        HeatmapItem.Day(
                            date = date,
                            status = statusOf(habit, completed, date, createdOn, today),
                            isAlternateMonth = isAlternate,
                        )
                }
                monday = monday.plusWeeks(1)
            }
            return items
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
            const val DAYS_PER_WEEK = 7

            /** A month gutter plus its seven days; the grid is laid out in this many columns. */
            const val COLUMNS = DAYS_PER_WEEK + 1

            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
