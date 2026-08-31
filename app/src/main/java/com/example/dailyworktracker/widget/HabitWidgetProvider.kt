package com.example.dailyworktracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.dailyworktracker.MainActivity
import com.example.dailyworktracker.R
import com.example.dailyworktracker.data.model.TodayHabit
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.util.DateProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Draws the home screen widget: a header summarising today, and a list of the habits due.
 *
 * The rows themselves come from [HabitWidgetFactory]; only the parts outside the list are built
 * here, which is why this needs to read the habits at all — the summary counts them.
 */
@AndroidEntryPoint
class HabitWidgetProvider : AppWidgetProvider() {
    @Inject
    lateinit var repository: HabitRepository

    @Inject
    lateinit var dateProvider: DateProvider

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Reading the habits is a database round trip, and onUpdate runs on the main thread.
        // goAsync keeps the receiver alive across it, which is the sanctioned way to do slow work
        // in a broadcast without the process being killed mid-query.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val habits = repository.observeHabitsFor(dateProvider.today()).first()
                appWidgetIds.forEach { id -> render(context, appWidgetManager, id, habits) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun render(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        habits: List<TodayHabit>,
    ) {
        val views =
            RemoteViews(context.packageName, R.layout.widget_habits).apply {
                setViewVisibility(
                    R.id.textWidgetSummary,
                    if (habits.isEmpty()) View.GONE else View.VISIBLE,
                )
                setTextViewText(
                    R.id.textWidgetSummary,
                    context.getString(
                        R.string.widget_summary,
                        habits.count { it.isCompleted },
                        habits.size,
                    ),
                )
                setRemoteAdapter(R.id.listWidgetHabits, rowFactoryIntent(context, appWidgetId))
                setEmptyView(R.id.listWidgetHabits, R.id.textWidgetEmpty)
                setPendingIntentTemplate(R.id.listWidgetHabits, toggleTemplate(context))
                setOnClickPendingIntent(R.id.widgetHeader, openAppIntent(context))
            }

        appWidgetManager.updateAppWidget(appWidgetId, views)
        // updateAppWidget re-binds the adapter but is entitled to reuse rows it has already
        // cached, so the list is told separately that its contents are stale.
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.listWidgetHabits)
    }

    /**
     * An adapter is identified by its Intent, and `Intent.filterEquals` — which is what the
     * comparison uses — ignores extras. Two widgets would therefore share one factory, and with it
     * one set of rows. Repeating the widget id in the data URI is the long-standing way to make the
     * intents genuinely distinct.
     */
    private fun rowFactoryIntent(
        context: Context,
        appWidgetId: Int,
    ): Intent =
        Intent(context, HabitWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }

    /**
     * The single PendingIntent every row fills in with its own habit id.
     *
     * It has to be mutable: filling in that id is precisely the mutation a collection
     * performs on its template, and an immutable one would deliver every tap with no id.
     * FLAG_MUTABLE is a compile-time constant, so naming it here is safe below API 31,
     * where the bit simply goes unread.
     */
    private fun toggleTemplate(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, HabitWidgetActionReceiver::class.java).apply {
                action = HabitWidgetActionReceiver.ACTION_TOGGLE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        /**
         * Asks every placed widget to redraw.
         *
         * Does nothing when the user has not placed one, so the write path pays only an
         * AppWidgetManager lookup for a feature nobody is using.
         */
        fun requestUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val ids =
                appWidgetManager.getAppWidgetIds(
                    ComponentName(context, HabitWidgetProvider::class.java),
                )
            if (ids.isEmpty()) return

            context.sendBroadcast(
                Intent(context, HabitWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }
    }
}
