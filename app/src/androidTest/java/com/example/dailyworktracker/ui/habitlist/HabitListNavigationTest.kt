package com.example.dailyworktracker.ui.habitlist

import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dailyworktracker.MainActivity
import com.example.dailyworktracker.R
import com.google.android.material.appbar.MaterialToolbar
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
 * in `onViewCreated` landed on a detached hierarchy. Symptom was the toolbar menu vanishing after
 * navigating to All habits and back.
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
    fun toolbarMenuSurvivesNavigatingAwayAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                // Navigate programmatically: clicking the menu would need the very thing under test.
                scenario.navController().navigate(R.id.action_habitList_to_allHabits)
            }

            pressBack()

            onView(withId(R.id.toolbar)).check { view, _ ->
                val toolbar = view as MaterialToolbar
                assertNotNull(
                    "Toolbar menu lost its item after returning from All habits",
                    toolbar.menu.findItem(R.id.action_all_habits),
                )
            }
        }
    }

    @Test
    fun toolbarMenuIsNotDuplicatedOnRepeatedNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(2) {
                scenario.onActivity {
                    scenario.navController().navigate(R.id.action_habitList_to_allHabits)
                }
                pressBack()
            }

            onView(withId(R.id.toolbar)).check { view, _ ->
                val toolbar = view as MaterialToolbar
                assertEquals(
                    "Menu was inflated more than once into the same toolbar",
                    1,
                    toolbar.menu.size(),
                )
            }
        }
    }

    @Test
    fun listContentSurvivesNavigatingAwayAndBack() {
        // The stale binding also left the RecyclerView and state views unconfigured.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                scenario.navController().navigate(R.id.action_habitList_to_allHabits)
            }

            pressBack()

            onView(withId(R.id.fabAddHabit)).check(matches(isDisplayed()))
        }
    }
}
