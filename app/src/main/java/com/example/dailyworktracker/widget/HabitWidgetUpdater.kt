package com.example.dailyworktracker.widget

/**
 * Tells the home screen widget that the habits or their completions have changed.
 *
 * An interface, like the reminder scheduler, so the repository that calls it on every write stays a
 * plain class that unit tests can build without an AppWidgetManager.
 */
interface HabitWidgetUpdater {
    fun onHabitsChanged()
}
