package com.example.dailyworktracker.data.model

import com.example.dailyworktracker.data.local.entity.Habit

/**
 * How a habit is recorded each day.
 *
 * The two kinds are separate types rather than a nullable amount on one type, so a habit cannot be
 * half of each. The stored form is a single nullable column - see [toGoal] - but nothing above the
 * repository works with that column directly.
 */
sealed interface HabitGoal {
    /** Ticked off with a checkbox. */
    data object Once : HabitGoal

    /**
     * Logged as a number: 12 pages, 30 minutes, 3 times.
     *
     * The number is a record of what was done, not a target to reach. Any amount at all completes
     * the day; there is deliberately no "not done until N".
     */
    data class Amount(val unit: HabitUnit) : HabitGoal
}

/** The units a habit can be logged in. Display only - the counting is identical for all of them. */
enum class HabitUnit {
    TIMES,
    PAGES,
    MINUTES,
}

/**
 * Reads the goal out of the stored column.
 *
 * An unrecognised unit falls back to [HabitGoal.Once] rather than throwing: a row written by a
 * newer version of the app should leave the habit usable, not crash the list that draws it.
 */
fun Habit.toGoal(): HabitGoal {
    val unit = goalUnit?.let { stored -> HabitUnit.entries.firstOrNull { it.name == stored } }
    return if (unit == null) HabitGoal.Once else HabitGoal.Amount(unit)
}

/** Writes the goal back to the stored column. */
fun Habit.withGoal(goal: HabitGoal): Habit =
    copy(
        goalUnit =
            when (goal) {
                HabitGoal.Once -> null
                is HabitGoal.Amount -> goal.unit.name
            },
    )
