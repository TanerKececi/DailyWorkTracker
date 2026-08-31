package com.example.dailyworktracker.ui.habitdetail

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
    val heatmap: List<HeatmapCellUiModel>,
)
