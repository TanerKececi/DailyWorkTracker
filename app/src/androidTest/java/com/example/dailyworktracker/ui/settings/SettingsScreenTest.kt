package com.example.dailyworktracker.ui.settings

import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dailyworktracker.MainActivity
import com.example.dailyworktracker.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings holds the debug seeder and nothing else.
 *
 * The mockup defines no Settings content, so an honestly sparse screen is the deliberate design
 * rather than a stub waiting to be filled.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
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
    fun settingsOffersTheSeederInADebugBuild() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { scenario.navController().navigate(R.id.settingsFragment) }

            onView(withId(R.id.buttonSeedSampleData)).check(matches(isDisplayed()))
        }
    }
}
