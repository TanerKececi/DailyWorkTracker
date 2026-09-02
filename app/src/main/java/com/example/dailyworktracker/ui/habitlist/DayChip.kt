package com.example.dailyworktracker.ui.habitlist

import java.time.LocalDate

/**
 * One day on the date strip.
 *
 * [isSelected] and [isToday] travel with the day rather than being compared in the adapter, so a
 * selection change is an ordinary list change DiffUtil can animate: only the two days that actually
 * changed rebind.
 */
data class DayChip(
    val date: LocalDate,
    val isSelected: Boolean,
    /** Marked apart from selected: today is still worth pointing out while a past day is shown. */
    val isToday: Boolean,
)
