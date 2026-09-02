package com.example.dailyworktracker.ui.common

import android.content.res.ColorStateList
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.core.view.updateLayoutParams
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.R
import com.example.dailyworktracker.data.model.HabitUnit
import com.example.dailyworktracker.databinding.ItemAmountBarBinding
import com.example.dailyworktracker.ui.habitdetail.ChartBar
import com.example.dailyworktracker.ui.habitdetail.ChartRange
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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

/** Shows a validation message on a text field, or clears it when the resource is absent. */
@BindingAdapter("errorTextRes")
fun TextInputLayout.setErrorTextRes(
    @StringRes resId: Int?,
) {
    error = resId?.let(context::getString)
}

/**
 * Labels the reminder button with the time, and describes it for screen readers.
 *
 * Both come from one adapter because the description embeds the same formatted time; setting them
 * apart would let them drift.
 */
@BindingAdapter("reminderTimeText")
fun TextView.setReminderTimeText(time: LocalTime) {
    val formatted = TimeFormatter.format(context, time)
    text = formatted
    contentDescription = context.getString(R.string.add_habit_reminder_time_description, formatted)
}

/**
 * Labels the amount logged for a day, e.g. "12 pages".
 *
 * A null amount means nothing has been recorded yet and the button invites a first entry, rather
 * than reading as a zero the user did not enter. Each unit has its own plural so the singular is
 * right in every language - "1 page", not "1 pages".
 */
@BindingAdapter(value = ["loggedAmount", "loggedUnit"], requireAll = true)
fun TextView.setLoggedAmount(
    amount: Int?,
    unit: HabitUnit?,
) {
    text =
        if (amount == null || unit == null) {
            context.getString(R.string.habit_amount_none)
        } else {
            resources.getQuantityString(unit.pluralRes(), amount, amount)
        }
}

@PluralsRes
private fun HabitUnit.pluralRes(): Int =
    when (this) {
        HabitUnit.TIMES -> R.plurals.habit_amount_times
        HabitUnit.PAGES -> R.plurals.habit_amount_pages
        HabitUnit.MINUTES -> R.plurals.habit_amount_minutes
    }

/**
 * Draws the amount chart: one column per day, sized against the best day in view.
 *
 * Built in code rather than with a chart library. Seven bars is a row of weighted columns, and
 * the smallest charting dependency is a megabyte and an API to learn for arithmetic that fits in
 * this function.
 *
 * Scaling is relative to the tallest bar rather than to a fixed axis, so a habit logged in pages
 * and one logged in minutes both fill the space. The value sits above each bar, which is what
 * makes an axis unnecessary at this size.
 */
@BindingAdapter(value = ["amountBars", "barRange"], requireAll = true)
fun LinearLayout.setAmountBars(
    bars: List<ChartBar>?,
    range: ChartRange?,
) {
    removeAllViews()
    val days = bars.orEmpty()
    if (days.isEmpty()) return

    val inflater = LayoutInflater.from(context)
    val maxAmount = days.maxOf { it.amount }
    val fullHeight = resources.getDimensionPixelSize(R.dimen.amount_chart_height)
    val locale = Locale.getDefault()
    // A yearly bar covers a month, so it is labelled with the month rather than a date.
    val skeleton = if (range == ChartRange.YEAR) BAR_MONTH_SKELETON else BAR_DATE_SKELETON
    val labelFormat =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    // The bar drawable is shared with the heatmap, which tints it at bind time rather than
    // carrying a colour of its own. Untinted it draws as an all but invisible outline.
    val barTint =
        ColorStateList.valueOf(
            MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary),
        )

    days.forEach { day ->
        val bar = ItemAmountBarBinding.inflate(inflater, this, false)

        // A day with nothing logged gets no bar at all, so an empty day reads as empty rather
        // than as a very small amount.
        val barHeight = if (maxAmount == 0) 0 else fullHeight * day.amount / maxAmount
        bar.viewBar.backgroundTintList = barTint
        bar.viewBar.updateLayoutParams { height = barHeight }
        bar.spaceBar.updateLayoutParams { height = fullHeight - barHeight }

        bar.textBarValue.text = if (day.amount == 0) "" else day.amount.toString()
        bar.textBarDate.text = labelFormat.format(day.start)
        addView(bar.root)
    }

    // Opens on the most recent days rather than a month ago. Posted because the columns have not
    // been measured yet, so there is nothing to scroll to until after this layout pass.
    (parent as? HorizontalScrollView)?.let { scroller ->
        scroller.post { scroller.fullScroll(View.FOCUS_RIGHT) }
    }
}

/**
 * Month and day, ordered the way the device orders them - "8/26" in the US, "26.08." in Germany.
 *
 * A skeleton rather than a literal pattern, matching TimeFormatter: the fields wanted are month and
 * day, and which order they go in is the locale's business. The year is left out because the chart
 * never spans one and the columns are narrow.
 */
private const val BAR_DATE_SKELETON = "Md"

/** Just the month, for the yearly view. */
private const val BAR_MONTH_SKELETON = "MMM"

/**
 * The running total under the grid, for a habit logged as a number.
 *
 * Days completed used to share this line, but it is a Records tile now, and one number in two
 * places is one number that can disagree with itself. The line is hidden entirely for a
 * ticked-off habit, which has nothing to total.
 */
@BindingAdapter(value = ["totalAmount", "totalUnit"], requireAll = true)
fun TextView.setTotalAmount(
    amount: Int,
    unit: HabitUnit?,
) {
    text =
        unit?.let {
            context.getString(
                R.string.habit_detail_total_amount,
                resources.getQuantityString(it.pluralRes(), amount, amount),
            )
        }
}

/**
 * Writes a month as "Jul 2024".
 *
 * Formatted here rather than in the ViewModel for the usual reason: naming a month needs a Locale,
 * which belongs to the view layer. The year is always shown, because the stepper walks backwards
 * without limit and a bare month name would quietly claim to be this year's.
 */
@BindingAdapter("monthLabel")
fun TextView.setMonthLabel(month: YearMonth?) {
    if (month == null) return
    val name = month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    text = "$name ${month.year}"
}
