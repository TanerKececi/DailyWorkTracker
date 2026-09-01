package com.example.dailyworktracker.ui.common

import android.view.View
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.R
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
 * two lines tall. `requireAll = false` matters: a habit with no reminder passes null here, and the
 * line must still render its schedule.
 */
@BindingAdapter(value = ["scheduleBitmask", "reminderTime"], requireAll = false)
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
