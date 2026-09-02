# Bottom Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the toolbar-overflow navigation with a persistent bottom bar — Home / My habits / + / Settings — and add a Settings screen holding the debug seeder.

**Architecture:** One Activity, one flat nav graph, unchanged. A `BottomNavigationView` is added to `activity_main.xml` and bound with `setupWithNavController`; its item-selected listener is then replaced so the centre `+` opens the existing add/edit bottom sheet without ever becoming the selected tab. Destinations stay flat — no nested graph per tab, so no per-tab saved back stacks.

**Tech Stack:** Kotlin, Android Views with data binding, Material 3 `BottomNavigationView`, Navigation Component with SafeArgs, Hilt, JUnit 4 + Espresso for instrumented tests.

**Spec:** `docs/superpowers/specs/2026-09-02-bottom-nav-and-progress-design.md`

## Global Constraints

- **Build command** (PowerShell, this machine only):
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:TEMP = "C:\gradle-tmp"; $env:TMP = "C:\gradle-tmp"; .\gradlew.bat :app:ktlintCheck :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon`
- `connectedAndroidTest` does **not** accept `--tests`. Filter with `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
- `connectedDebugAndroidTest` leaves the app **uninstalled** when it finishes. Reinstall with `adb install -r app/build/outputs/apk/debug/app-debug.apk` before any manual emulator check.
- Do not pipe gradle through `Select-String` — it mangles `$LASTEXITCODE`.
- **Bound widgets use `android:onClick`, never `OnCheckedChangeListener`.**
- **Never put a Java-style cast in a layout**, and never pass a bare `null` in a binding expression.
- **Emoji in data-bound layouts must be XML character references** (`&#x1F3C3;`).
- Every Fragment uses `private val binding by viewBinding(XxxBinding::bind)`. Do not "simplify" `ViewBindingDelegate`.
- Debug-only affordances must be behind `BuildConfig.DEBUG` so they cannot appear in a release build.
- ktlint is enforced: a body expression that fits on the signature line must be on it.

---

### Task 1: Settings screen with the debug seeder

Settings must exist as a destination before the bar can point at it. This task also moves the seeder off the habit list, which removes the last thing anchoring a Snackbar to the FAB that Task 3 deletes.

**Files:**
- Create: `app/src/main/java/com/example/dailyworktracker/ui/settings/SettingsFragment.kt`
- Create: `app/src/main/res/layout/fragment_settings.xml`
- Create: `app/src/androidTest/java/com/example/dailyworktracker/ui/settings/SettingsScreenTest.kt`
- Modify: `app/src/main/res/navigation/nav_graph.xml` (add the destination)
- Modify: `app/src/main/res/values/strings.xml` (add `settings_title`)
- Modify: `app/src/main/java/com/example/dailyworktracker/ui/habitlist/HabitListFragment.kt` (remove `seedSampleData`, the `SampleDataSeeder` injection, and the `action_seed_sample_data` menu branch)
- Modify: `app/src/main/res/menu/menu_habit_list.xml` (remove `action_seed_sample_data`)

**Interfaces:**
- Consumes: `SampleDataSeeder.seed()` (suspend, existing, injected with `@Inject lateinit var`); `viewBinding` delegate from `ui.common`.
- Produces: destination id `R.id.settingsFragment`; view id `R.id.buttonSeedSampleData`; string `R.string.settings_title`. Task 2 binds a menu item to `R.id.settingsFragment`.

- [ ] **Step 1: Write the failing test**

`app/src/androidTest/java/com/example/dailyworktracker/ui/settings/SettingsScreenTest.kt`:

```kotlin
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
 * Settings holds the debug seeder and nothing else. The mockup defines no Settings content, so an
 * honestly sparse screen is the deliberate design rather than a stub waiting to be filled.
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
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:TEMP = "C:\gradle-tmp"; $env:TMP = "C:\gradle-tmp"; .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon "-Pandroid.testInstrumentationRunnerArguments.class=com.example.dailyworktracker.ui.settings.SettingsScreenTest"`

Expected: compile failure — `R.id.settingsFragment` and `R.id.buttonSeedSampleData` do not resolve. That is a legitimate red: the destination and the view genuinely do not exist yet.

- [ ] **Step 3: Add the string**

In `app/src/main/res/values/strings.xml`, after the `all_habits_title` line:

```xml
    <string name="settings_title">Settings</string>
```

- [ ] **Step 4: Create the layout**

`app/src/main/res/layout/fragment_settings.xml`. Plain view binding — this screen has no state to bind, so no `<layout>` wrapper:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/appBarLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:fitsSystemWindows="true">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:title="@string/settings_title" />

    </com.google.android.material.appbar.AppBarLayout>

    <!--
      Deliberately near-empty: the mockup defines no Settings content, and inventing some would be
      worse than a sparse screen. The seeder lives here because it is a development aid, not a
      feature, and it has no other home now the habit list has no overflow menu.
    -->
    <com.google.android.material.button.MaterialButton
        android:id="@+id/buttonSeedSampleData"
        style="@style/Widget.Material3.Button.TextButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="8dp"
        android:gravity="start|center_vertical"
        android:paddingHorizontal="16dp"
        android:text="@string/debug_seed_sample_data"
        app:layout_behavior="@string/appbar_scrolling_view_behavior" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 5: Create the Fragment**

`app/src/main/java/com/example/dailyworktracker/ui/settings/SettingsFragment.kt`:

```kotlin
package com.example.dailyworktracker.ui.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.dailyworktracker.BuildConfig
import com.example.dailyworktracker.R
import com.example.dailyworktracker.data.sample.SampleDataSeeder
import com.example.dailyworktracker.databinding.FragmentSettingsBinding
import com.example.dailyworktracker.ui.common.viewBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the debug seeder and nothing else.
 *
 * The mockup defines no Settings content. Rather than invent features to fill the screen, it stays
 * sparse and gives the seeder a home now that the habit list has no overflow menu.
 */
@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private val binding by viewBinding(FragmentSettingsBinding::bind)

    /** Only ever used behind a `BuildConfig.DEBUG` check. */
    @Inject
    lateinit var sampleDataSeeder: SampleDataSeeder

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        // Sample data is a development aid; it must never be reachable in a release build.
        binding.buttonSeedSampleData.visibility =
            if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        binding.buttonSeedSampleData.setOnClickListener { seedSampleData() }
    }

    private fun seedSampleData() {
        viewLifecycleOwner.lifecycleScope.launch {
            sampleDataSeeder.seed()
            Snackbar.make(
                binding.root,
                R.string.debug_sample_data_inserted,
                Snackbar.LENGTH_SHORT,
            ).show()
        }
    }
}
```

- [ ] **Step 6: Add the destination**

In `app/src/main/res/navigation/nav_graph.xml`, after the `allHabitsFragment` fragment element:

```xml
    <fragment
        android:id="@+id/settingsFragment"
        android:name="com.example.dailyworktracker.ui.settings.SettingsFragment"
        android:label="@string/settings_title"
        tools:layout="@layout/fragment_settings" />
```

- [ ] **Step 7: Remove the seeder from the habit list**

In `HabitListFragment.kt`, delete the `sampleDataSeeder` field and its `@Inject`, the whole `seedSampleData()` function, and the `R.id.action_seed_sample_data ->` branch plus the `menu.findItem(R.id.action_seed_sample_data).isVisible = BuildConfig.DEBUG` line in `setUpToolbarMenu()`. Remove the now-unused `BuildConfig` and `SampleDataSeeder` imports.

In `app/src/main/res/menu/menu_habit_list.xml`, delete the `action_seed_sample_data` item and the comment above it.

- [ ] **Step 8: Run the test and confirm it passes**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:TEMP = "C:\gradle-tmp"; $env:TMP = "C:\gradle-tmp"; .\gradlew.bat :app:ktlintCheck :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon`

Expected: all pass. `HabitListNavigationTest` still passes at this point — `action_all_habits` and the FAB are untouched until Task 3.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: give the debug seeder a Settings screen to live on"
```

---

### Task 2: The bottom bar

**Files:**
- Create: `app/src/main/res/menu/menu_bottom_nav.xml`
- Create: `app/src/main/res/drawable/ic_home.xml`, `ic_list.xml`, `ic_settings.xml`
- Create: `app/src/androidTest/java/com/example/dailyworktracker/ui/BottomNavTest.kt`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/example/dailyworktracker/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml` (tab labels)

**Interfaces:**
- Consumes: `R.id.settingsFragment` (Task 1); existing `R.id.habitListFragment`, `R.id.allHabitsFragment`, `R.id.habitDetailFragment`, `R.id.addEditHabitFragment`; `AddEditHabitViewModel.Companion.NEW_HABIT_ID`.
- Produces: view id `R.id.bottomNav`; menu item ids `R.id.habitListFragment`, `R.id.allHabitsFragment`, `R.id.action_add_habit`, `R.id.settingsFragment`; global action `R.id.action_global_addEditHabit`.

- [ ] **Step 1: Write the failing test**

`app/src/androidTest/java/com/example/dailyworktracker/ui/BottomNavTest.kt`:

```kotlin
package com.example.dailyworktracker.ui

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
 * tab exactly where it was. Wiring it as a destination would still "work" by eye, which is why
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
                    androidx.core.os.bundleOf("habitId" to 1L),
                )
            }

            scenario.onActivity { activity ->
                val bar = activity.findViewById<BottomNavigationView>(R.id.bottomNav)
                assertEquals(
                    "The bar belongs to tab roots, not to a pushed screen",
                    android.view.View.GONE,
                    bar.visibility,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:TEMP = "C:\gradle-tmp"; $env:TMP = "C:\gradle-tmp"; .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon "-Pandroid.testInstrumentationRunnerArguments.class=com.example.dailyworktracker.ui.BottomNavTest"`

Expected: compile failure — `R.id.bottomNav` and `R.id.action_add_habit` do not exist yet.

- [ ] **Step 3: Add the tab labels**

In `strings.xml`, in the habit-list block:

```xml
    <!-- Bottom navigation tab labels; kept short so five fit on a narrow screen -->
    <string name="nav_home">Home</string>
    <string name="nav_my_habits">My habits</string>
    <string name="nav_add_habit">Add</string>
    <string name="nav_settings">Settings</string>
```

- [ ] **Step 4: Add the three icons**

Follow the shape of the existing `ic_add.xml` exactly — 24dp, `viewportWidth`/`viewportHeight` 24, a single white-filled path, and **no `android:tint`** (the bar tints its own icons from the item colour state list; a hard-coded tint would defeat the selected/unselected states).

`app/src/main/res/drawable/ic_home.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z" />
</vector>
```

`app/src/main/res/drawable/ic_list.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M3,13h2v-2L3,11v2zM3,17h2v-2L3,15v2zM3,9h2L5,7L3,7v2zM7,13h14v-2L7,11v2zM7,17h14v-2L7,15v2zM7,7v2h14L21,7L7,7z" />
</vector>
```

`app/src/main/res/drawable/ic_settings.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,15.5A3.5,3.5 0 0,1 8.5,12A3.5,3.5 0 0,1 12,8.5A3.5,3.5 0 0,1 15.5,12A3.5,3.5 0 0,1 12,15.5M19.43,12.97C19.47,12.65 19.5,12.33 19.5,12C19.5,11.67 19.47,11.34 19.43,11L21.54,9.37C21.73,9.22 21.78,8.95 21.66,8.73L19.66,5.27C19.54,5.05 19.27,4.96 19.05,5.05L16.56,6.05C16.04,5.66 15.5,5.32 14.87,5.07L14.5,2.42C14.46,2.18 14.25,2 14,2H10C9.75,2 9.54,2.18 9.5,2.42L9.13,5.07C8.5,5.32 7.96,5.66 7.44,6.05L4.95,5.05C4.73,4.96 4.46,5.05 4.34,5.27L2.34,8.73C2.21,8.95 2.27,9.22 2.46,9.37L4.57,11C4.53,11.34 4.5,11.67 4.5,12C4.5,12.33 4.53,12.65 4.57,12.97L2.46,14.63C2.27,14.78 2.21,15.05 2.34,15.27L4.34,18.73C4.46,18.95 4.73,19.03 4.95,18.95L7.44,17.94C7.96,18.34 8.5,18.68 9.13,18.93L9.5,21.58C9.54,21.82 9.75,22 10,22H14C14.25,22 14.46,21.82 14.5,21.58L14.87,18.93C15.5,18.67 16.04,18.34 16.56,17.94L19.05,18.95C19.27,19.03 19.54,18.95 19.66,18.73L21.66,15.27C21.78,15.05 21.73,14.78 21.54,14.63L19.43,12.97Z" />
</vector>
```

- [ ] **Step 5: Create the bar's menu**

`app/src/main/res/menu/menu_bottom_nav.xml`. The ids of the three tabs deliberately **match destination ids** — that is how `NavigationUI` binds them with no mapping table. `action_add_habit` is the odd one out precisely because it is not a destination:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/habitListFragment"
        android:icon="@drawable/ic_home"
        android:title="@string/nav_home" />
    <item
        android:id="@+id/allHabitsFragment"
        android:icon="@drawable/ic_list"
        android:title="@string/nav_my_habits" />
    <!-- Not a destination: opens the add sheet and never becomes the selected tab. -->
    <item
        android:id="@+id/action_add_habit"
        android:icon="@drawable/ic_add"
        android:title="@string/nav_add_habit" />
    <item
        android:id="@+id/settingsFragment"
        android:icon="@drawable/ic_settings"
        android:title="@string/nav_settings" />
</menu>
```

- [ ] **Step 6: Add a global action for the add sheet**

The `+` is reachable from any tab, so the existing per-screen actions will not do. In `nav_graph.xml`, as a direct child of `<navigation>` (after the last `<fragment>`, before `</navigation>`):

```xml
    <!-- Global: the bottom bar's + opens the sheet from whichever tab is showing. -->
    <action
        android:id="@+id/action_global_addEditHabit"
        app:destination="@id/addEditHabitFragment" />
```

- [ ] **Step 7: Put the bar in the Activity layout**

Replace `app/src/main/res/layout/activity_main.xml` entirely:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/navHostFragment"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:defaultNavHost="true"
        app:layout_constraintBottom_toTopOf="@id/bottomNav"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:navGraph="@navigation/nav_graph" />

    <!--
      fitsSystemWindows so the bar itself takes the gesture-bar inset. Screens then only have to
      clear the bar, not the system bars as well - see the note in HabitListFragment.
    -->
    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottomNav"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:fitsSystemWindows="true"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:menu="@menu/menu_bottom_nav" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 8: Wire it in MainActivity**

Add these imports to `MainActivity.kt`:

```kotlin
import android.view.View
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.example.dailyworktracker.ui.addedithabit.AddEditHabitViewModel.Companion.NEW_HABIT_ID
import com.google.android.material.bottomnavigation.BottomNavigationView
```

Then, inside `onCreate` after the existing `reminderSync` block, add `setUpBottomNav()`, and add these members:

```kotlin
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
                    androidx.core.os.bundleOf("habitId" to NEW_HABIT_ID),
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
            setOf(R.id.habitListFragment, R.id.allHabitsFragment, R.id.settingsFragment)
    }
```

- [ ] **Step 9: Run the test and confirm it passes**

Run the filtered command from Step 2. Expected: all three `BottomNavTest` tests pass.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat: reach every area from a bottom bar"
```

---

### Task 3: Retire the old entry points

The bar now duplicates the FAB and the overflow menu. This task removes them and reworks the navigation test that asserted on them.

**Files:**
- Delete: `app/src/main/res/menu/menu_habit_list.xml`
- Modify: `app/src/main/res/layout/fragment_habit_list.xml` (remove the FAB)
- Modify: `app/src/main/java/com/example/dailyworktracker/ui/habitlist/HabitListFragment.kt`
- Modify: `app/src/main/res/layout/fragment_all_habits.xml` (remove the back arrow)
- Modify: `app/src/main/java/com/example/dailyworktracker/ui/allhabits/AllHabitsFragment.kt`
- Modify: `app/src/androidTest/java/com/example/dailyworktracker/ui/habitlist/HabitListNavigationTest.kt`

**Interfaces:**
- Consumes: `R.id.bottomNav` (Task 2), for Snackbar anchoring.
- Produces: nothing new. `R.id.fabAddHabit`, `R.id.action_all_habits` and `R.layout.menu_habit_list` cease to exist.

- [ ] **Step 1: Rework the navigation test first**

This is the step to take slowly. `HabitListNavigationTest` guards a real view-lifecycle bug — `ViewBindingDelegate` once handed a stale binding to a fresh view, so everything in `onViewCreated` landed on a detached hierarchy. The menu and FAB were only the visible symptoms, and this task deletes both.

Replace the three test methods with the two below. The toolbar-menu pair goes because the menu itself goes — there is no longer a menu to lose or to double-inflate. The surviving test keeps the guard by asserting on something `onViewCreated` configures and this PR keeps: the RecyclerView's adapter.

```kotlin
    @Test
    fun listContentSurvivesNavigatingAwayAndBack() {
        // The stale-binding bug left the RecyclerView unconfigured: onViewCreated ran against a
        // detached hierarchy, so the adapter was set on a view that was never shown. Asserting the
        // adapter is attached after a round trip is what is left of that guard now the FAB and the
        // toolbar menu - the old symptoms - have gone.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
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
```

Fix the imports: add `androidx.recyclerview.widget.RecyclerView`; remove `com.google.android.material.appbar.MaterialToolbar`, `androidx.test.espresso.assertion.ViewAssertions.matches` and `androidx.test.espresso.matcher.ViewMatchers.isDisplayed` if they are no longer referenced. Update the class KDoc so it describes the guard rather than the menu.

- [ ] **Step 2: Run it and confirm it still passes before anything is deleted**

Run: `... .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon "-Pandroid.testInstrumentationRunnerArguments.class=com.example.dailyworktracker.ui.habitlist.HabitListNavigationTest"`

Expected: PASS. The reworked test must be green *against the current code* — that proves the rework did not quietly become vacuous before the removals land.

- [ ] **Step 3: Remove the FAB from the layout**

In `fragment_habit_list.xml`, delete the whole `ExtendedFloatingActionButton` element with id `fabAddHabit`.

- [ ] **Step 4: Re-anchor the Snackbars and drop the menu from the Fragment**

In `HabitListFragment.kt`:

- Delete the `binding.fabAddHabit.setOnClickListener { ... }` line in `onViewCreated`.
- Delete the `setUpToolbarMenu()` function and its call in `onViewCreated`.
- Replace both `.setAnchorView(binding.fabAddHabit)` calls — one in `toggleSkip`, one in the archive branch of `showHabitMenu` — with `.setAnchorView(requireActivity().findViewById<View>(R.id.bottomNav))` so the Snackbar clears the bar instead of the deleted FAB.
- `navigateToHabitEditor` is still used by the row menu's Edit action, so it stays.
- Remove imports that are now unused: `R` stays, but drop `com.example.dailyworktracker.BuildConfig` if Task 1 has not already.

- [ ] **Step 5: Delete the menu resource**

```bash
git rm app/src/main/res/menu/menu_habit_list.xml
```

- [ ] **Step 6: Make All habits a tab root**

In `fragment_all_habits.xml`, delete the `app:navigationIcon="@drawable/ic_arrow_back"` attribute from the toolbar — it is a tab root now, not a pushed screen.

In `AllHabitsFragment.kt`, delete the `binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }` line, and the `androidx.navigation.fragment.findNavController` import if nothing else in the file uses it.

- [ ] **Step 7: Pad the tab roots for the bar**

The bar now takes the system inset via `fitsSystemWindows`, so a screen padding for the system inset as well would double it. In `applyWindowInsets()` in **both** `HabitListFragment.kt` and `AllHabitsFragment.kt`, replace the bottom-inset padding with the bar's height:

```kotlin
    /**
     * The bottom bar takes the gesture-bar inset itself, so the content only has to clear the bar.
     * Adding the system inset here as well would pad for it twice.
     */
    private fun applyWindowInsets() {
        val bottomNav = requireActivity().findViewById<View>(R.id.bottomNav)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            view.updatePadding(bottom = bottomNav.height)
            insets
        }
    }
```

- [ ] **Step 8: Run the full suite**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:TEMP = "C:\gradle-tmp"; $env:TMP = "C:\gradle-tmp"; .\gradlew.bat :app:ktlintCheck :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon`

Expected: everything green, including `BottomNavTest`, `SettingsScreenTest` and the reworked `HabitListNavigationTest`.

- [ ] **Step 9: Verify on the emulator, screen by screen**

`connectedDebugAndroidTest` uninstalls the app, so reinstall first:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then seed sample data from Settings and check each of these by eye:

- each of the three tabs opens its screen and the bar highlights the right item;
- `+` opens the add sheet and the previously selected tab is still highlighted behind it;
- opening a habit's History hides the bar, and pressing back brings it back;
- no list content is trapped behind the bar — scroll each tab to its last row;
- a Snackbar (swipe a row to skip it) appears above the bar, not behind it.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "refactor: retire the FAB and the overflow menu the bar replaces"
```

---

## Self-Review

**Spec coverage.** Shell → Task 2 steps 7-8. Tabs → Task 2 steps 3-5. Back stack (flat, `onNavDestinationSelected`) → Task 2 step 8. Bar visibility → Task 2 step 8, tested in step 1. `HabitListFragment` changes → Task 1 step 7 and Task 3 steps 3-4. `AllHabitsFragment` → Task 3 step 6. `menu_habit_list.xml` deleted → Task 3 step 5. `SettingsFragment` → Task 1 steps 4-6. Window insets → Task 3 step 7 and the `fitsSystemWindows` note in Task 2 step 7. Testing, including the `HabitListNavigationTest` rework → Task 3 steps 1-2. No spec section is unimplemented.

**Type consistency.** `R.id.bottomNav` is defined in Task 2 step 7 and used in Task 3 steps 4 and 7. `R.id.action_add_habit` is defined in Task 2 step 5 and used in step 8 and in the test in step 1. `R.id.settingsFragment` is created in Task 1 step 6 and consumed by the menu in Task 2 step 5 and by `TAB_DESTINATIONS` in step 8. `R.id.buttonSeedSampleData` is created in Task 1 step 4 and asserted in step 1. `NEW_HABIT_ID` is the existing constant on `AddEditHabitViewModel.Companion`, already imported this way by `HabitListFragment`.

**Ordering.** Task 1 creates the Settings destination before Task 2's menu points at it; Task 2 creates the bar before Task 3 anchors Snackbars to it and deletes the FAB. No task references something a later task creates.
