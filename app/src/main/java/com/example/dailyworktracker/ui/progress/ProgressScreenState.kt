package com.example.dailyworktracker.ui.progress

import androidx.annotation.StringRes
import com.example.dailyworktracker.R
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/** Which half of the toggle is showing. */
enum class ProgressMode {
    SUMMARY,
    HABITS,
}

/**
 * Everything the Progress screen draws.
 *
 * Flat accessors below exist for XML only. Binding expressions have no `when` and no smart-casting,
 * so without these the layout would need Java-style casts. Kotlin callers - ViewModel, tests -
 * match on [displayState] directly instead.
 */
data class ProgressScreenState(
    val month: YearMonth,
    val today: LocalDate,
    val mode: ProgressMode,
    val displayState: ProgressDisplayState,
) {
    /** A month that has not happened yet has nothing to show, so the stepper stops at this one. */
    val canGoToNextMonth: Boolean get() = month.isBefore(YearMonth.from(today))

    // Named "...Mode" deliberately: data binding de-prefixes `isHabits` to `state.habits`, which
    // would collide with the habits list below and resolve to whichever it found first.
    val isSummaryMode: Boolean get() = mode == ProgressMode.SUMMARY
    val isHabitsMode: Boolean get() = mode == ProgressMode.HABITS

    // flat accessors for XML only
    val isLoading: Boolean get() = displayState is ProgressDisplayState.Loading
    val isContent: Boolean get() = displayState is ProgressDisplayState.Content
    val isEmpty: Boolean get() = displayState is ProgressDisplayState.Empty
    val isError: Boolean get() = displayState is ProgressDisplayState.Error

    /** Summary shows the ring and the calendar; Habits shows the breakdown. Never both. */
    val showsCalendar: Boolean get() = isContent && isSummaryMode
    val showsHabits: Boolean get() = isContent && isHabitsMode

    /** The toggle is meaningless with nothing to toggle between. */
    val showsToggle: Boolean get() = isContent

    val rate: Float get() = (displayState as? ProgressDisplayState.Content)?.rate ?: 0f
    val percent: Int get() = (rate * 100).roundToInt()
    val days: List<CalendarDay> get() = displayState.days
    val habits: List<ProgressHabitUiModel> get() = displayState.habits

    val errorMessage: String?
        get() = (displayState as? ProgressDisplayState.Error)?.throwable?.localizedMessage

    @get:StringRes
    val emptyTitleRes: Int get() = R.string.progress_empty_title

    @get:StringRes
    val emptyMessageRes: Int get() = R.string.progress_empty_message
}

sealed interface ProgressDisplayState {
    data object Loading : ProgressDisplayState

    data class Content(
        val rate: Float,
        override val days: List<CalendarDay>,
        override val habits: List<ProgressHabitUiModel>,
    ) : ProgressDisplayState

    /**
     * No active habits at all.
     *
     * Distinct from a real 0%, which means habits exist and were missed. Collapsing the two would
     * have the screen accuse an empty app of failing.
     */
    data object Empty : ProgressDisplayState

    data class Error(val throwable: Throwable) : ProgressDisplayState

    /** Empty in every state but Content, so the lists can bind without the layout branching. */
    val days: List<CalendarDay> get() = emptyList()

    val habits: List<ProgressHabitUiModel> get() = emptyList()
}
