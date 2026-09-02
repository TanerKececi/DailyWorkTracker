package com.example.dailyworktracker.ui.progress

import kotlin.math.roundToInt

/**
 * One row of the per-habit breakdown, already reduced to what the view draws.
 *
 * Keeping the screen's model separate from the entity means storage changes do not ripple into the
 * UI, and DiffUtil can compare rows by value.
 */
data class ProgressHabitUiModel(
    val id: Long,
    val title: String,
    val emoji: String,
    val rate: Float,
) {
    /** Rounded once here, so the bar and the number beside it cannot disagree. */
    val percent: Int get() = (rate * 100).roundToInt()
}
