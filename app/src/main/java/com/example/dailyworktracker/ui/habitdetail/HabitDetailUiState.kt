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
    /**
     * The last few days, for the chart.
     *
     * Calendar days rather than scheduled ones: a gap in the bars is itself information, and
     * skipping unscheduled days would make the dates along the bottom jump.
     */
    val recentAmounts: List<DailyAmount> = emptyList(),
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

/** One bar of the amount chart: a day, and what was logged on it. */
data class DailyAmount(
    val date: LocalDate,
    val amount: Int,
)
