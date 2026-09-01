package com.example.dailyworktracker.data.model

import com.example.dailyworktracker.data.local.entity.Habit

/**
 * Which part of the day a habit belongs to.
 *
 * A habit need not have one: null means "no particular time", which is what every habit was before
 * this existed. Modelled as a nullable enum rather than a fourth ANY value so there is one way to
 * say it, and so the storage column and the domain agree on what absence means.
 *
 * Independent of the reminder. This says when the habit belongs in the day; the reminder decides
 * when to be nagged about it, and a morning habit may perfectly well remind in the evening.
 */
enum class TimeOfDay {
    MORNING,
    AFTERNOON,
    EVENING,
}

/**
 * Reads the part of the day out of the stored column.
 *
 * An unrecognised value reads as null rather than throwing: a row written by a newer version of
 * the app should leave the habit usable, not crash the list that draws it.
 */
fun Habit.timeOfDay(): TimeOfDay? = timeOfDay?.let { stored -> TimeOfDay.entries.firstOrNull { it.name == stored } }

/** Writes the part of the day back to the stored column. */
fun Habit.withTimeOfDay(time: TimeOfDay?): Habit = copy(timeOfDay = time?.name)
