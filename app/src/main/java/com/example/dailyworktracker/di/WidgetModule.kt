package com.example.dailyworktracker.di

import com.example.dailyworktracker.widget.AppWidgetHabitWidgetUpdater
import com.example.dailyworktracker.widget.HabitWidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Wires up the home screen widget's link back to the data layer. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds
    @Singleton
    abstract fun bindHabitWidgetUpdater(impl: AppWidgetHabitWidgetUpdater): HabitWidgetUpdater
}
