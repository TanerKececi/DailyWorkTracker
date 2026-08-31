package com.example.dailyworktracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. [HiltAndroidApp] triggers generation of the app's DI component, which
 * every `@AndroidEntryPoint` Activity and Fragment attaches to.
 */
@HiltAndroidApp
class DailyWorkTrackerApp : Application()
