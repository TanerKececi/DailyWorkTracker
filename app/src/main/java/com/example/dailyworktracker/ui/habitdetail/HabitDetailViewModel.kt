package com.example.dailyworktracker.ui.habitdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.model.HabitGoal
import com.example.dailyworktracker.data.model.toGoal
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.ui.common.DayStatus
import com.example.dailyworktracker.util.DateProvider
import com.example.dailyworktracker.util.HabitStatistics
import com.example.dailyworktracker.util.HabitVisibility
import com.example.dailyworktracker.util.StreakCalculator
import com.example.dailyworktracker.util.WeekdaySchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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

        private val chartRange = MutableStateFlow(ChartRange.WEEK)

        /** Which span the chart draws. Screen state, not stored: it resets with the screen. */
        fun onChartRangeSelected(range: ChartRange) {
            chartRange.value = range
        }

        val uiState: StateFlow<HabitDetailDisplayState> =
            combine(
                repository.observeHabit(habitId),
                repository.observeCompletions(habitId),
                repository.observeSkips(habitId),
                chartRange,
            ) { habit, completions, skipped, range ->
                // Archiving or deleting the habit elsewhere leaves this screen with nothing to show.
                if (habit == null) {
                    HabitDetailDisplayState.Missing
                } else {
                    HabitDetailDisplayState.Content(buildState(habit, completions, skipped, range))
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
            completions: Map<LocalDate, Int?>,
            skipped: Set<LocalDate>,
            range: ChartRange,
        ): HabitDetailUiState {
            // Streaks and statistics only ever ask which days have a record, never how much.
            val completed = completions.keys
            val today = dateProvider.today()
            val createdOn = HabitVisibility.createdDate(habit)

            return HabitDetailUiState(
                title = habit.title,
                emoji = habit.emoji,
                scheduleDaysBitmask = habit.scheduleDaysBitmask,
                currentStreak =
                    StreakCalculator.currentStreak(completed, habit.scheduleDaysBitmask, today, skipped),
                longestStreak =
                    StreakCalculator.longestStreak(completed, habit.scheduleDaysBitmask, today, skipped),
                completionRate =
                    HabitStatistics.completionRate(
                        completedDates = completed,
                        scheduleDaysBitmask = habit.scheduleDaysBitmask,
                        from = createdOn,
                        to = today,
                        skippedDates = skipped,
                    ),
                completedCount = completed.size,
                heatmap = buildHeatmap(habit, completions, skipped, createdOn, today),
                unit = (habit.toGoal() as? HabitGoal.Amount)?.unit,
                totalAmount = completions.values.sumOf { it ?: 0 },
                chartRange = range,
                chartBars = chartBars(completions, today, range),
            )
        }

        /**
         * The chart's bars, oldest first so they read left to right.
         *
         * A period with nothing recorded contributes a zero-height bar rather than being left out,
         * so the labels along the bottom stay evenly spaced and a gap reads as a gap.
         */
        private fun chartBars(
            completions: Map<LocalDate, Int?>,
            today: LocalDate,
            range: ChartRange,
        ): List<ChartBar> =
            if (range == ChartRange.YEAR) {
                monthlyBars(completions, today)
            } else {
                (range.days - 1 downTo 0).map { back ->
                    val date = today.minusDays(back)
                    ChartBar(start = date, amount = completions[date] ?: 0)
                }
            }

        /**
         * Twelve months, each bar the sum of everything logged in it.
         *
         * Summed rather than averaged: the question a year answers is how much was done, and an
         * average would quietly hide a month with one enormous day in it.
         */
        private fun monthlyBars(
            completions: Map<LocalDate, Int?>,
            today: LocalDate,
        ): List<ChartBar> {
            val totals =
                completions.entries
                    .groupBy { YearMonth.from(it.key) }
                    .mapValues { (_, entries) -> entries.sumOf { it.value ?: 0 } }
            val thisMonth = YearMonth.from(today)

            return (MONTHS_SHOWN - 1 downTo 0).map { back ->
                val month = thisMonth.minusMonths(back)
                ChartBar(start = month.atDay(1), amount = totals[month] ?: 0)
            }
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
            completions: Map<LocalDate, Int?>,
            skipped: Set<LocalDate>,
            createdOn: LocalDate,
            today: LocalDate,
        ): List<HeatmapItem> {
            val completed = completions.keys
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
                            status = statusOf(habit, completed, skipped, date, createdOn, today),
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
            skipped: Set<LocalDate>,
            date: LocalDate,
            createdOn: LocalDate,
            today: LocalDate,
        ): DayStatus =
            when {
                date.isAfter(today) || date.isBefore(createdOn) -> DayStatus.OUT_OF_RANGE
                !WeekdaySchedule.isScheduledOn(habit.scheduleDaysBitmask, date.dayOfWeek) ->
                    DayStatus.NOT_SCHEDULED

                // A day nothing was due on is never "skipped", so this sits below the schedule test.
                date in skipped -> DayStatus.SKIPPED
                date in completed -> DayStatus.COMPLETED
                // Today has not resolved yet, so it is neither a miss nor an off-day.
                date == today -> DayStatus.PENDING
                else -> DayStatus.MISSED
            }

        companion object {
            const val ARG_HABIT_ID = "habitId"
            const val WEEKS_SHOWN = 12

            /** Bars in the yearly view, one per month. */
            const val MONTHS_SHOWN = 12L
            const val DAYS_PER_WEEK = 7

            /** A month gutter plus its seven days; the grid is laid out in this many columns. */
            const val COLUMNS = DAYS_PER_WEEK + 1

            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
