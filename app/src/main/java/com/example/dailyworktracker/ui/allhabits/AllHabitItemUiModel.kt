package com.example.dailyworktracker.ui.allhabits

/** One row of the manage-habits list. Unlike the today list, there is no per-day completion here. */
data class AllHabitItemUiModel(
    val id: Long,
    val title: String,
    val emoji: String,
    val scheduleDaysBitmask: Int,
    val isArchived: Boolean,
)
