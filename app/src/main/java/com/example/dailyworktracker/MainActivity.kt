package com.example.dailyworktracker

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.dailyworktracker.reminder.HabitReminderSync
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The app's only Activity. It exists purely to host the navigation graph; every screen is a
 * Fragment destination, so there is no per-screen Activity plumbing to maintain.
 *
 * Window insets are handled by the destination layouts themselves rather than here, so each screen
 * can decide what draws edge to edge.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main) {
    @Inject
    lateinit var reminderSync: HabitReminderSync

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Only on a genuinely new launch: a rotation re-creates the Activity with the same pending
        // reminders, and re-arming them from scratch each time would be work for nothing.
        if (savedInstanceState == null) {
            lifecycleScope.launch { reminderSync.resyncAll() }
        }
    }
}
