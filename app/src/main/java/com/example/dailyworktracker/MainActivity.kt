package com.example.dailyworktracker

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.example.dailyworktracker.reminder.HabitReminderSync
import com.example.dailyworktracker.ui.addedithabit.AddEditHabitViewModel.Companion.NEW_HABIT_ID
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The app's only Activity. It hosts the navigation graph and the bottom bar; every screen is a
 * Fragment destination, so there is no per-screen Activity plumbing to maintain.
 *
 * Window insets are handled by the destination layouts themselves rather than here, so each screen
 * can decide what draws edge to edge. The bar is the exception: it consumes the bottom system inset
 * for everyone, because it is the thing sitting against that edge.
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

        setUpBottomNav()
    }

    /**
     * Binds the bar to the graph, then takes its item listener back.
     *
     * `setupWithNavController` installs two things: an item-selected listener on the view, and an
     * OnDestinationChangedListener on the controller. Replacing the first leaves the second in
     * place, so the bar still follows the current destination on navigation and on back - which is
     * why this reads as safe despite looking like it undoes the setup call.
     */
    private fun setUpBottomNav() {
        val navController = navController()
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        bottomNav.setupWithNavController(navController)
        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.action_add_habit) {
                navController.navigate(
                    R.id.action_global_addEditHabit,
                    bundleOf("habitId" to NEW_HABIT_ID),
                )
                // false: the + is an action, so it must never become the selected tab.
                false
            } else {
                NavigationUI.onNavDestinationSelected(item, navController)
            }
        }

        // The bar belongs to tab roots. A pushed screen and the add sheet get the whole window.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNav.visibility =
                if (destination.id in TAB_DESTINATIONS) View.VISIBLE else View.GONE
        }
    }

    private fun navController(): NavController =
        (supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment)
            .navController

    private companion object {
        val TAB_DESTINATIONS =
            setOf(
                R.id.habitListFragment,
                R.id.allHabitsFragment,
                R.id.progressFragment,
                R.id.settingsFragment,
            )
    }
}
