package com.example.dailyworktracker.ui.habitlist

import androidx.annotation.StringRes
import com.example.dailyworktracker.R
import java.time.LocalDate

/**
 * Everything the habit list screen draws.
 *
 * The date bar renders in every display state - it lives in the AppBarLayout precisely so it
 * survives an empty or failed day, since otherwise there would be no control left to navigate back
 * with. That makes [selectedDate] and [today] shared data, which is why they sit on this data class
 * rather than inside [displayState]: a sealed hierarchy would have to repeat them in every case.
 *
 * Keeping them here also removes the screen's last piece of business logic from the Fragment, which
 * used to ask the ViewModel for today's date and compare dates itself.
 */
data class HabitListScreenState(
    val selectedDate: LocalDate,
    val today: LocalDate,
    val displayState: HabitListDisplayState,
) {
    /** Future days cannot be completed, so stepping forward stops at today. */
    val canGoToNextDay: Boolean get() = selectedDate.isBefore(today)

    /** Only shown off today, where it is the fastest way back. */
    val showJumpToToday: Boolean get() = selectedDate != today

    /*
     * Flat accessors below exist for XML only. Binding expressions have no `when` and no
     * smart-casting, so without these the layout would need Java-style casts. Kotlin callers -
     * ViewModel, tests - match on `displayState` directly instead.
     */

    val isLoading: Boolean get() = displayState is HabitListDisplayState.Loading

    val isContent: Boolean get() = displayState is HabitListDisplayState.Content

    val isError: Boolean get() = displayState is HabitListDisplayState.Error

    val isEmpty: Boolean get() = emptyTitleRes != null

    val habits: List<HabitListItemUiModel> get() = displayState.habits

    val errorMessage: String?
        get() = (displayState as? HabitListDisplayState.Error)?.throwable?.localizedMessage

    @get:StringRes
    val emptyTitleRes: Int?
        get() =
            when (displayState) {
                HabitListDisplayState.NoHabitsYet -> R.string.habit_list_empty_title
                HabitListDisplayState.NothingScheduled -> R.string.habit_list_nothing_scheduled_title
                else -> null
            }

    @get:StringRes
    val emptyMessageRes: Int?
        get() =
            when (displayState) {
                HabitListDisplayState.NoHabitsYet -> R.string.habit_list_empty_message
                HabitListDisplayState.NothingScheduled -> R.string.habit_list_nothing_scheduled_message
                else -> null
            }
}

/**
 * The mutually exclusive halves of the screen: the list, one of the two empties, or a failure.
 *
 * The two empties are separate objects rather than one case carrying string resources. They are
 * genuinely different situations - no habits exist at all, versus habits exist but none repeats on
 * this day - and modelling them apart keeps the ViewModel out of the business of choosing copy.
 */
sealed interface HabitListDisplayState {
    data object Loading : HabitListDisplayState

    data class Content(override val habits: List<HabitListItemUiModel>) : HabitListDisplayState

    /** Habits exist, but none is scheduled for the selected day. */
    data object NothingScheduled : HabitListDisplayState

    /** No habits have been created yet. */
    data object NoHabitsYet : HabitListDisplayState

    data class Error(val throwable: Throwable) : HabitListDisplayState

    /** Empty in every state but [Content], so the list can bind without the layout branching. */
    val habits: List<HabitListItemUiModel> get() = emptyList()
}
