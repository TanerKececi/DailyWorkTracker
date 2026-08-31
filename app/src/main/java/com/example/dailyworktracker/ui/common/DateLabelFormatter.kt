package com.example.dailyworktracker.ui.common

import android.content.Context
import com.example.dailyworktracker.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns a date into the label shown on the habit list, e.g. "Today", "Yesterday" or "Wed, 3 Sep".
 *
 * Lives in the view layer because it needs a [Context]; the ViewModel passes dates, never strings.
 * Mirrors the split already used by [ScheduleFormatter].
 */
object DateLabelFormatter {
    private const val PATTERN = "EEE, d MMM"

    fun format(
        context: Context,
        date: LocalDate,
        today: LocalDate,
    ): String =
        when (date) {
            today -> context.getString(R.string.date_today)
            today.minusDays(1) -> context.getString(R.string.date_yesterday)
            else ->
                DateTimeFormatter
                    .ofPattern(PATTERN, Locale.getDefault())
                    .format(date)
        }
}
