package com.example.dailyworktracker

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps in [HiltTestApplication] for instrumentation tests, so they get a Hilt component without
 * going through [DailyWorkTrackerApp].
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        name: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)

    /**
     * Stands WorkManager up for tests.
     *
     * The app removes WorkManager's own startup initializer so the Application can supply a Hilt
     * worker factory — but under test the Application *is* [HiltTestApplication], which supplies
     * nothing, and the first injection of a screen that reaches the scheduler blew up with
     * "WorkManager is not initialized properly". Initialising it here restores what the real
     * Application would have done, and the synchronous executor keeps enqueued work off a
     * background thread where a test could not see it.
     */
    override fun onStart() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            targetContext,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        super.onStart()
    }
}
