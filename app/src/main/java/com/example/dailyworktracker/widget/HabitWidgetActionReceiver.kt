package com.example.dailyworktracker.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.util.DateProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ticks a habit off when its row is tapped on the home screen.
 *
 * Separate from [HabitWidgetProvider], which must be exported so the AppWidget framework can
 * broadcast to it. This one stays unexported, so nothing outside the app can tick a habit off — a
 * PendingIntent carries the identity of the app that created it, so the launcher can still fire it.
 *
 * It asks for its dependencies rather than being an `@AndroidEntryPoint`. Hilt injects a plain
 * BroadcastReceiver from a superclass it substitutes after compilation, reached by calling
 * `super.onReceive(...)`; Kotlin refuses to compile that, because as far as the source is concerned
 * `BroadcastReceiver.onReceive` is abstract. An entry point is the same graph, asked for out loud.
 */
class HabitWidgetActionReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun habitRepository(): HabitRepository

        fun dateProvider(): DateProvider
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_TOGGLE) return

        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, NO_HABIT_ID)
        if (habitId == NO_HABIT_ID) return

        val dependencies =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                Dependencies::class.java,
            )

        // The write outlives onReceive, so the process has to be kept alive across it.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // Always today: the widget only ever shows today, and the past is the app's job.
                dependencies.habitRepository().toggleCompletion(
                    habitId = habitId,
                    date = dependencies.dateProvider().today(),
                )
            } finally {
                // The write goes through the repository, which is what redraws the widget.
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.example.dailyworktracker.widget.action.TOGGLE"
        const val EXTRA_HABIT_ID = "habitId"

        private const val NO_HABIT_ID = -1L
    }
}
