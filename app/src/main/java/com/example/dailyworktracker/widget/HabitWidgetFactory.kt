package com.example.dailyworktracker.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.dailyworktracker.R
import com.example.dailyworktracker.data.model.TodayHabit
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.util.DateProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Builds one row per habit due today, for the widget's list. */
class HabitWidgetFactory(
    private val context: Context,
    private val repository: HabitRepository,
    private val dateProvider: DateProvider,
) : RemoteViewsService.RemoteViewsFactory {
    private var habits: List<TodayHabit> = emptyList()

    override fun onCreate() = load()

    /**
     * Called on a binder thread and explicitly allowed to block, which is the only reason
     * [runBlocking] is defensible here: a factory is asked for its rows synchronously and has no
     * way to deliver them later.
     */
    override fun onDataSetChanged() = load()

    private fun load() {
        habits = runBlocking { repository.observeHabitsFor(dateProvider.today()).first() }
    }

    override fun onDestroy() {
        habits = emptyList()
    }

    override fun getCount(): Int = habits.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = habits[position]
        return RemoteViews(context.packageName, R.layout.widget_habit_item).apply {
            setTextViewText(R.id.textWidgetEmoji, item.habit.emoji)
            setTextViewText(R.id.textWidgetTitle, item.habit.title)

            setViewVisibility(
                R.id.textWidgetStreak,
                if (item.currentStreak > 0) View.VISIBLE else View.GONE,
            )
            setTextViewText(
                R.id.textWidgetStreak,
                context.getString(R.string.widget_streak, item.currentStreak),
            )

            setImageViewResource(
                R.id.imageWidgetCheck,
                if (item.isCompleted) R.drawable.ic_widget_checked else R.drawable.ic_widget_unchecked,
            )
            setContentDescription(R.id.imageWidgetCheck, checkDescription(item))

            // Rows in a collection cannot carry their own PendingIntent; they fill in the
            // template the provider set on the list, and the extras are what say which habit.
            setOnClickFillInIntent(
                R.id.widgetItemRoot,
                Intent().putExtra(HabitWidgetActionReceiver.EXTRA_HABIT_ID, item.habit.id),
            )
        }
    }

    private fun checkDescription(item: TodayHabit): String =
        if (item.isCompleted) {
            context.getString(R.string.widget_item_done, item.habit.title)
        } else {
            context.getString(R.string.habit_completed_checkbox, item.habit.title)
        }

    /** Rows are cheap to build, so there is nothing worth showing while one is prepared. */
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = habits[position].habit.id

    override fun hasStableIds(): Boolean = true
}
