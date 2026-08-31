package com.example.dailyworktracker.ui.habitdetail

import java.time.LocalDate
import java.time.YearMonth

/** How a single day should be drawn in the history grid. */
enum class DayStatus {
    /** Due that day and done. */
    COMPLETED,

    /** Due that day and missed. Only ever assigned to days that have already resolved. */
    MISSED,

    /** The habit does not repeat on that weekday. */
    NOT_SCHEDULED,

    /** Due today and not done yet. Distinct from [MISSED]: the day has not resolved. */
    PENDING,

    /** Before the habit existed, or later than today. Drawn as an empty slot. */
    OUT_OF_RANGE,
}

/**
 * One slot in the history grid.
 *
 * Each row is a month gutter followed by seven days, so the grid is laid out as eight equal columns
 * and every row lines up with the weekday header without any span arithmetic.
 */
sealed interface HeatmapItem {
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
    ) : HeatmapItem

    data class Day(
        val date: LocalDate,
        val status: DayStatus,
    ) : HeatmapItem
}
