package com.example.dailyworktracker.ui.habitlist

import androidx.annotation.StringRes
import com.example.dailyworktracker.R

/**
 * Which part of the day's list a habit belongs to.
 *
 * Derived from the day's state, never stored: a habit is "done" because that day has a completion,
 * exactly as everywhere else in the app.
 */
enum class HabitSection(
    @get:StringRes val titleRes: Int,
) {
    IN_PROGRESS(R.string.habit_list_section_in_progress),
    DONE(R.string.habit_list_section_done),
    SKIPPED(R.string.habit_list_section_skipped),
}

/**
 * One line of the habit list: a section heading, or a habit under it.
 *
 * Headings are list items rather than a wrapping layout so the whole screen stays one RecyclerView
 * with one DiffUtil - a habit moving from In progress to Done is then an ordinary list change, and
 * the section counts update with it.
 */
sealed interface HabitListRow {
    data class Header(
        val section: HabitSection,
        /** Shown beside the title, which is the only reason an empty section is omitted entirely. */
        val count: Int,
    ) : HabitListRow

    data class Habit(val item: HabitListItemUiModel) : HabitListRow
}
