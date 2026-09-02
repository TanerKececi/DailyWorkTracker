package com.example.dailyworktracker.ui.habitlist

import androidx.annotation.StringRes
import com.example.dailyworktracker.R
import com.example.dailyworktracker.data.model.TimeOfDay
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
    /**
     * The first day the strip can scroll back to: the oldest habit's creation date.
     *
     * Everything the app has history for is therefore reachable by scrolling, which is what lets
     * the date bar drop its arrows without losing the ability to backfill.
     */
    val firstDate: LocalDate = today,
    /** Which part of the day the list is filtered to; null is "all day". */
    val timeFilter: TimeOfDay? = null,
    val displayState: HabitListDisplayState,
) {
    /**
     * The date strip, oldest first so it reads left to right and ends on today.
     *
     * Built once per state rather than in a getter: the layout binds it, and a getter would rebuild
     * the whole range on every bind.
     */
    val days: List<DayChip> = buildDays(firstDate, selectedDate, today)

    /*
     * Flat accessors below exist for XML only. Binding expressions have no `when` and no
     * smart-casting, so without these the layout would need Java-style casts. Kotlin callers -
     * ViewModel, tests - match on `displayState` directly instead.
     */

    val isLoading: Boolean get() = displayState is HabitListDisplayState.Loading

    val isContent: Boolean get() = displayState is HabitListDisplayState.Content

    val isError: Boolean get() = displayState is HabitListDisplayState.Error

    val isEmpty: Boolean get() = emptyTitleRes != null

    val rows: List<HabitListRow> get() = displayState.rows

    val errorMessage: String?
        get() = (displayState as? HabitListDisplayState.Error)?.throwable?.localizedMessage

    @get:StringRes
    val emptyTitleRes: Int?
        get() =
            when (displayState) {
                HabitListDisplayState.NoHabitsYet -> R.string.habit_list_empty_title
                HabitListDisplayState.NothingScheduled -> R.string.habit_list_nothing_scheduled_title
                HabitListDisplayState.NothingAtThisTime -> R.string.habit_list_nothing_at_time_title
                else -> null
            }

    @get:StringRes
    val emptyMessageRes: Int?
        get() =
            when (displayState) {
                HabitListDisplayState.NoHabitsYet -> R.string.habit_list_empty_message
                HabitListDisplayState.NothingScheduled -> R.string.habit_list_nothing_scheduled_message
                HabitListDisplayState.NothingAtThisTime -> R.string.habit_list_nothing_at_time_message
                else -> null
            }
}

/**
 * Every day from [firstDate] through [today], inclusive.
 *
 * Clamped so the range can never be empty or run backwards: an app with no habits still shows
 * today, because an empty strip would be a blank row rather than an honest "nothing yet".
 */
private fun buildDays(
    firstDate: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
): List<DayChip> {
    val start = minOf(firstDate, today)
    return generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { date ->
            DayChip(date = date, isSelected = date == selectedDate, isToday = date == today)
        }
        .toList()
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

    /**
     * The day's habits, grouped into sections.
     *
     * [habits] is kept beside [rows] because most callers - and every test about a habit's own
     * state - care about the habits themselves and not about which heading they landed under.
     */
    data class Content(
        override val habits: List<HabitListItemUiModel>,
    ) : HabitListDisplayState {
        override val rows: List<HabitListRow> = groupIntoSections(habits)
    }

    /** Habits exist, but none is scheduled for the selected day. */
    data object NothingScheduled : HabitListDisplayState

    /** No habits have been created yet. */
    data object NoHabitsYet : HabitListDisplayState

    /** Habits are due today, just none in the part of the day being shown. */
    data object NothingAtThisTime : HabitListDisplayState

    data class Error(val throwable: Throwable) : HabitListDisplayState

    /** Empty in every state but [Content], so the list can bind without the layout branching. */
    val habits: List<HabitListItemUiModel> get() = emptyList()

    val rows: List<HabitListRow> get() = emptyList()
}

/**
 * Splits the day's habits into In progress, Done and Skipped, each behind its own heading.
 *
 * A section with nothing in it is left out rather than shown empty: the heading carries a count, so
 * "Done (0)" would be a line of noise saying what the absence already says.
 *
 * Order within a section is the order the repository gave, so grouping never quietly re-sorts the
 * list underneath the user.
 */
private fun groupIntoSections(habits: List<HabitListItemUiModel>): List<HabitListRow> {
    val bySection =
        habits.groupBy { habit ->
            when {
                habit.isSkipped -> HabitSection.SKIPPED
                habit.isCompleted -> HabitSection.DONE
                else -> HabitSection.IN_PROGRESS
            }
        }

    // Iterating the enum rather than the map keeps the sections in their declared order.
    return HabitSection.entries.flatMap { section ->
        val inSection = bySection[section].orEmpty()
        if (inSection.isEmpty()) {
            emptyList()
        } else {
            listOf(HabitListRow.Header(section, inSection.size)) + inSection.map(HabitListRow::Habit)
        }
    }
}
