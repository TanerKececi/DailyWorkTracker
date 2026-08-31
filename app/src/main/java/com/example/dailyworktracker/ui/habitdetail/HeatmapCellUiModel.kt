package com.example.dailyworktracker.ui.habitdetail

import java.time.LocalDate

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

data class HeatmapCellUiModel(
    val date: LocalDate,
    val status: DayStatus,
)
