package com.example.dailyworktracker.di

import android.content.Context
import androidx.work.WorkManager
import com.example.dailyworktracker.reminder.HabitReminderScheduler
import com.example.dailyworktracker.reminder.WorkManagerHabitReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Wires up reminder scheduling. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds
    @Singleton
    abstract fun bindHabitReminderScheduler(impl: WorkManagerHabitReminderScheduler): HabitReminderScheduler

    companion object {
        /**
         * WorkManager is initialised on demand — the manifest removes its startup provider — so this
         * first call is what builds it, using the Hilt worker factory from the Application.
         */
        @Provides
        @Singleton
        fun provideWorkManager(
            @ApplicationContext context: Context,
        ): WorkManager = WorkManager.getInstance(context)
    }
}
