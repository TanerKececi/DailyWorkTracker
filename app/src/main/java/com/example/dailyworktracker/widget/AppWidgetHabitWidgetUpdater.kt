package com.example.dailyworktracker.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nudges [HabitWidgetProvider] to redraw.
 *
 * It sends a broadcast rather than rendering here, which looks indirect but avoids a dependency
 * cycle: drawing the widget needs to read the habits, and this is called *from* the repository that
 * would have to supply them. Letting the provider do its own reading keeps the arrows pointing one
 * way, and gives the work a receiver's lifetime to run in rather than a write's.
 */
@Singleton
class AppWidgetHabitWidgetUpdater
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : HabitWidgetUpdater {
        override fun onHabitsChanged() {
            HabitWidgetProvider.requestUpdate(context)
        }
    }
