package com.example.dailyworktracker.ui.habitdetail

import com.example.dailyworktracker.data.model.HabitUnit
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Everything the detail screen draws.
 *
 * Numbers arrive already computed and the schedule stays a bitmask, so the ViewModel needs no
 * Context and the view keeps all formatting.
 */
data class HabitDetailUiState(
    val title: String,
    val emoji: String,
    val scheduleDaysBitmask: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    /** Share of resolved scheduled days that were completed, in `0f..1f`. */
    val completionRate: Float,
    val completedCount: Int,
    val heatmap: List<HeatmapItem>,
    /** The unit this habit is logged in, or null when it is simply ticked off. */
    val unit: HabitUnit? = null,
    /** Everything ever logged, added up. Zero for a ticked-off habit. */
    val totalAmount: Int = 0,
    /** Which range the chart is showing, so the toggle can highlight it. */
    val chartRange: ChartRange = ChartRange.WEEK,
    /**
     * The bars themselves.
     *
     * Every period in range appears, including ones with nothing logged: a gap is itself
     * information, and dropping empty periods would make the labels along the bottom jump.
     */
    val chartBars: List<ChartBar> = emptyList(),
) {
    /** Whether the amount chart and the amount total belong on screen at all. */
    val isAmountTracked: Boolean get() = unit != null

    /** The rate as whole percent, so the layout formats a number instead of doing arithmetic. */
    val completionPercent: Int get() = (completionRate * PERCENT).roundToInt()

    /** The grid starts at the habit's first week, so the heading names the span actually drawn. */
    val weeksShown: Int get() = heatmap.count { it is HeatmapItem.WeekGutter }

    private companion object {
        const val PERCENT = 100
    }
}

/**
 * How far back the amount chart looks.
 *
 * A year is aggregated by month rather than drawn as 365 bars: at one bar a day the columns would
 * be narrower than their own labels, and the question a year answers is about months anyway.
 */
enum class ChartRange(val days: Long) {
    WEEK(7),
    MONTH(30),

    /** Twelve months, one bar each; [days] is unused for this one. */
    YEAR(0),
}

/**
 * One bar of the amount chart.
 *
 * [start] is the day the bar covers, or the first of the month for a yearly bar - which is what
 * lets the view label it without being told which range produced it.
 */
data class ChartBar(
    val start: LocalDate,
    val amount: Int,
)
