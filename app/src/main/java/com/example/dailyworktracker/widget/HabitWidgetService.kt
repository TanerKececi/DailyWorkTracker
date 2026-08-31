package com.example.dailyworktracker.widget

import android.content.Intent
import android.widget.RemoteViewsService
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.util.DateProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hands the widget's list its row factory.
 *
 * The service exists only so Hilt has somewhere to inject: [HabitWidgetFactory] is constructed by
 * us, not by the framework, so it can take its dependencies as plain constructor arguments.
 */
@AndroidEntryPoint
class HabitWidgetService : RemoteViewsService() {
    @Inject
    lateinit var repository: HabitRepository

    @Inject
    lateinit var dateProvider: DateProvider

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = HabitWidgetFactory(applicationContext, repository, dateProvider)
}
