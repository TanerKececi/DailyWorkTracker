package com.example.dailyworktracker.ui.habitdetail

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
) {
    /** The rate as whole percent, so the layout formats a number instead of doing arithmetic. */
    val completionPercent: Int get() = (completionRate * PERCENT).roundToInt()

    /** The grid starts at the habit's first week, so the heading names the span actually drawn. */
    val weeksShown: Int get() = heatmap.count { it is HeatmapItem.WeekGutter }

    private companion object {
        const val PERCENT = 100
    }
}
