package com.example.dailyworktracker.ui.habitdetail

import com.example.dailyworktracker.ui.common.DayStatus
import java.time.LocalDate
import java.time.YearMonth

/**
 * One slot in the history grid.
 *
 * Each row is a month gutter followed by seven days, so the grid is laid out as eight equal columns
 * and every row lines up with the weekday header without any span arithmetic.
 *
 * Consecutive months alternate [isAlternateMonth], which the view turns into a background band. A
 * week straddling a month boundary therefore changes shade partway along, showing exactly where one
 * month ends without needing a separator row.
 */
sealed interface HeatmapItem {
    val isAlternateMonth: Boolean

    /**
     * The left-hand gutter for a week.
     *
     * [month] is null unless this week starts a new one, so the label appears once per month rather
     * than on every row. Kept as a [YearMonth] rather than text: naming the month is the view's job.
     */
    data class WeekGutter(
        /** The week this gutter belongs to; its identity for DiffUtil, since [month] is usually null. */
        val weekStart: LocalDate,
        val month: YearMonth?,
        override val isAlternateMonth: Boolean,
    ) : HeatmapItem

    data class Day(
        val date: LocalDate,
        val status: DayStatus,
        override val isAlternateMonth: Boolean,
    ) : HeatmapItem
}
