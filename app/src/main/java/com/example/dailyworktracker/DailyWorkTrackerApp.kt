package com.example.dailyworktracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. [HiltAndroidApp] triggers generation of the app's DI component, which
 * every `@AndroidEntryPoint` Activity and Fragment attaches to.
 *
 * It also supplies WorkManager's configuration, because the reminder worker takes constructor
 * dependencies and so has to be built by Hilt's factory rather than WorkManager's default one.
 */
@HiltAndroidApp
class DailyWorkTrackerApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
