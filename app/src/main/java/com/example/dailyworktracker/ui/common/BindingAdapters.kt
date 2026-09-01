package com.example.dailyworktracker.ui.common

import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.R
import java.time.LocalDate
import java.time.LocalTime

/*
 * Binding adapters shared by every data-bound layout.
 *
 * These exist so layouts can express *what* to show while the formatting stays in one place. Any
 * adapter needing a Context takes it from the view it binds, which is why formatters like
 * ScheduleFormatter can stay in the view layer without a ViewModel ever holding a Context.
 */

/**
 * Shows or hides a view, collapsing it rather than leaving a hole.
 *
 * `GONE` rather than `INVISIBLE` because every current use is a whole state group - a spinner, an
 * empty message, an error block - where reserving the space would misalign what is on screen.
 */
@BindingAdapter("visibleIf")
fun View.setVisibleIf(isVisible: Boolean) {
    visibility = if (isVisible) View.VISIBLE else View.GONE
}

/**
 * Hands a list to whatever [ListAdapter] the RecyclerView is already using.
 *
 * The adapter is still built in Kotlin, because its row click callbacks need the clicked view as a
 * popup anchor. Only the list itself travels through the layout.
 */
@BindingAdapter("items")
fun RecyclerView.setItems(items: List<Any>?) {
    @Suppress("UNCHECKED_CAST")
    (adapter as? ListAdapter<Any, *>)?.submitList(items.orEmpty())
}

/**
 * Writes the schedule, with the reminder time appended when the habit has one.
 *
 * Both halves are set by one adapter so the reminder can ride on the schedule line and keep a row
 * two lines tall. `requireAll = true` keeps this distinct from the schedule-only adapter below:
 * this one applies only where the layout binds both attributes. A habit with no reminder still
 * binds `reminderTime` - as null - and gets its schedule.
 */
@BindingAdapter(value = ["scheduleBitmask", "reminderTime"], requireAll = true)
fun TextView.setScheduleLine(
    bitmask: Int,
    reminderTime: LocalTime?,
) {
    val schedule = ScheduleFormatter.format(context, bitmask)
    text =
        if (reminderTime == null) {
            schedule
        } else {
            context.getString(
                R.string.all_habits_schedule_with_reminder,
                schedule,
                TimeFormatter.format(context, reminderTime),
            )
        }
}

/**
 * Sets text from a string resource that may be absent.
 *
 * Null means "this state has no copy of its own", which is how a screen with more than one empty
 * state leaves the choice of wording to the state itself rather than to the ViewModel.
 */
@BindingAdapter("textRes")
fun TextView.setTextRes(
    @StringRes resId: Int?,
) {
    if (resId == null || resId == 0) text = null else setText(resId)
}

/** Labels a date relative to today, e.g. "Today", "Yesterday" or "Wed, 3 Sep". */
@BindingAdapter(value = ["dateLabel", "relativeTo"], requireAll = true)
fun TextView.setDateLabel(
    date: LocalDate,
    today: LocalDate,
) {
    text = DateLabelFormatter.format(context, date, today)
}

/** The toolbar shows the same label as the date button, so it needs the same formatting. */
@BindingAdapter(value = ["dateLabel", "relativeTo"], requireAll = true)
fun Toolbar.setDateLabel(
    date: LocalDate,
    today: LocalDate,
) {
    title = DateLabelFormatter.format(context, date, today)
}

/** Writes the schedule on its own, for rows that have no reminder to append. */
@BindingAdapter("scheduleBitmask")
fun TextView.setSchedule(bitmask: Int) {
    text = ScheduleFormatter.format(context, bitmask)
}

/**
 * Shows the run of consecutive days, or hides the badge entirely.
 *
 * A zero streak is not worth a badge; it would only add noise to a fresh habit.
 */
@BindingAdapter("streakCount")
fun TextView.setStreakCount(streak: Int) {
    setVisibleIf(streak > 0)
    if (streak > 0) {
        text = resources.getQuantityString(R.plurals.habit_streak, streak, streak)
    }
}

/** Heading for the history grid, naming the number of weeks it actually draws. */
@BindingAdapter("weeksShown")
fun TextView.setWeeksShown(weeks: Int) {
    text = resources.getQuantityString(R.plurals.habit_detail_last_weeks, weeks, weeks)
}

/** Running total of completions, including days that fall outside the visible grid. */
@BindingAdapter("totalCompletions")
fun TextView.setTotalCompletions(count: Int) {
    text = resources.getQuantityString(R.plurals.habit_detail_total_completions, count, count)
}
