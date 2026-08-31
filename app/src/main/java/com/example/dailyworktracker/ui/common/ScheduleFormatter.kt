package com.example.dailyworktracker.ui.common

import android.content.Context
import com.example.dailyworktracker.R
import com.example.dailyworktracker.util.WeekdaySchedule
import java.time.format.TextStyle
import java.util.Locale

/**
 * Turns a weekday bitmask into display text, e.g. "Every day" or "Mon, Wed, Fri".
 *
 * Lives in the view layer because it needs a [Context]. Day names come from [java.time.DayOfWeek]
 * so they are localized by the platform rather than hand-maintained in strings.xml.
 */
object ScheduleFormatter {

    fun format(context: Context, bitmask: Int): String = when {
        !WeekdaySchedule.hasAnyDay(bitmask) -> context.getString(R.string.schedule_no_days)
        WeekdaySchedule.isEveryDay(bitmask) -> context.getString(R.string.schedule_every_day)
        else -> WeekdaySchedule.toDays(bitmask).joinToString(separator = ", ") { day ->
            day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
    }
}
