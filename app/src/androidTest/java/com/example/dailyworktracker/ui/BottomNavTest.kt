package com.example.dailyworktracker.ui

import android.view.View
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dailyworktracker.MainActivity
import com.example.dailyworktracker.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bar's wiring, which only a real navigator can exercise.
 *
 * The centre + is an action, not a destination: it opens the add sheet and must leave the selected
 * tab exactly where it was. Wiring it as a destination would still look right by eye, which is why
 * that assertion is here.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BottomNavTest {
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

    private fun ActivityScenario<MainActivity>.selectedTab(): Int {
        var selected = 0
        onActivity { activity ->
            selected = activity.findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId
        }
        return selected
    }

    @Test
    fun selectingATabChangesTheDestination() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.allHabitsFragment)).perform(click())

            assertEquals(
                R.id.allHabitsFragment,
                scenario.navController().currentDestination?.id,
            )
        }
    }

    @Test
    fun theAddButtonOpensTheSheetWithoutSelectingATab() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.action_add_habit)).perform(click())

            assertEquals(
                "The + opens the add sheet",
                R.id.addEditHabitFragment,
                scenario.navController().currentDestination?.id,
            )
            assertEquals(
                "The + is an action, so the selected tab must not move to it",
                R.id.habitListFragment,
                scenario.selectedTab(),
            )
        }
    }

    @Test
    fun theBarIsHiddenOnTheDetailScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                scenario.navController().navigate(
                    R.id.habitDetailFragment,
                    bundleOf("habitId" to 1L),
                )
            }

            scenario.onActivity { activity ->
                val bar = activity.findViewById<BottomNavigationView>(R.id.bottomNav)
                assertEquals(
                    "The bar belongs to tab roots, not to a pushed screen",
                    View.GONE,
                    bar.visibility,
                )
            }
        }
    }

    @Test
    fun theProgressTabIsReachable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.progressFragment)).perform(click())

            assertEquals(
                R.id.progressFragment,
                scenario.navController().currentDestination?.id,
            )
        }
    }
}
