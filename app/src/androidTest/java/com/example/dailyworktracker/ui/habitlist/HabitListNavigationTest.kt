package com.example.dailyworktracker.ui.habitlist

import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dailyworktracker.MainActivity
import com.example.dailyworktracker.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for a view-lifecycle bug that unit tests structurally cannot catch: the
 * ViewBinding delegate used to hand a stale binding to a freshly created view, so everything set up
 * in `onViewCreated` landed on a detached hierarchy.
 *
 * The original symptoms were a vanishing toolbar menu and a vanishing FAB. Both have since been
 * replaced by the bottom bar, so the assertions moved to what is left of `onViewCreated`'s work -
 * the RecyclerView's adapter. The bug being guarded against is the same one; only its visible
 * symptom changed.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HabitListNavigationTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private fun ActivityScenario<MainActivity>.navController(): NavController {
        lateinit var controller: NavController
        onActivity { activity ->
            val host =
                activity.supportFragmentManager
                    .findFragmentById(R.id.navHostFragment) as NavHostFragment
            controller = host.navController
        }
        return controller
    }

    @Test
    fun listContentSurvivesNavigatingAwayAndBack() {
        // The stale binding left the RecyclerView unconfigured: onViewCreated ran against a
        // detached hierarchy, so the adapter was set on a view that was never shown.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                // Navigate programmatically: clicking a tab would exercise the bar, not this.
                scenario.navController().navigate(R.id.allHabitsFragment)
            }

            pressBack()

            onView(withId(R.id.recyclerHabits)).check { view, _ ->
                assertNotNull(
                    "The habit list came back without its adapter, so onViewCreated ran against a dead view",
                    (view as RecyclerView).adapter,
                )
            }
        }
    }

    @Test
    fun theListIsStillTheStartDestinationAfterARoundTrip() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                scenario.navController().navigate(R.id.allHabitsFragment)
            }

            pressBack()

            assertEquals(R.id.habitListFragment, scenario.navController().currentDestination?.id)
        }
    }
}
