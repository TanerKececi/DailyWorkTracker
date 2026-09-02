package com.example.dailyworktracker.ui.progress

import com.example.dailyworktracker.ui.common.DayStatus
import java.time.LocalDate

/**
 * One cell of the month grid.
 *
 * [date] is null for the blank slots before the 1st. They are real items rather than an offset the
 * view works out, so the grid can be a plain seven-column list and every date lands under its own
 * weekday without any span arithmetic.
 */
data class CalendarDay(
    val date: LocalDate?,
    val status: DayStatus,
)
