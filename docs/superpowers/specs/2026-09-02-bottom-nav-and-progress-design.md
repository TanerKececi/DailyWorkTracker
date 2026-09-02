# Bottom navigation and the Progress tab

## Why

Every screen the app has beyond the habit list is reached through a toolbar
overflow menu. "All habits" is one menu item deep, and the planned Progress and
Settings screens have nowhere to live at all. The mockup replaces that with a
persistent bottom bar — Home / My habits / + / Progress / Settings — which makes
each area a peer rather than something hidden behind a menu.

This is a navigation restructure *and* a new screen, so it ships as two pull
requests. PR A moves the existing screens into a bottom bar and adds Settings.
PR B adds the Progress tab and its screen.

## Decisions already made

Settled before design; not to be re-litigated.

- The centre **`+` is an action, not a destination.** It opens the existing
  `AddEditHabitFragment` bottom sheet and never becomes the selected tab. The
  habit list's "Add habit" FAB is removed — two entry points for one action is
  redundant, and a FAB would sit on top of the bottom bar.
- The Progress **ring shows the completion rate for the current month across all
  active habits**, matching the period of the calendar beneath it.
- The **Summary / Habits toggle switches aggregate against per-habit**: Summary
  is the ring and calendar for all habits combined; Habits is the same period
  broken down one row per habit.
- **Settings gets the debug "Insert sample data" action and nothing else.** The
  mockup defines no Settings content, and inventing some is worse than an
  honestly sparse screen.

## Out of scope

The detail heatmap still has no `SKIPPED` day status, so a past skipped day
renders red as `MISSED` there. The Progress calendar in PR B will face the same
question. Deliberately deferred, and called out here so PR B does not silently
inherit it as a decision.

---

# PR A — bottom navigation

## Shell

`activity_main.xml` becomes a container holding the existing
`FragmentContainerView` plus a `BottomNavigationView`.

`MainActivity` wires it in two steps:

```kotlin
bottomNav.setupWithNavController(navController)
bottomNav.setOnItemSelectedListener { item ->
    if (item.itemId == R.id.action_add_habit) { openAddSheet(); false }
    else NavigationUI.onNavDestinationSelected(item, navController)
}
```

Returning `false` for `+` is what stops it becoming the selected tab.

Replacing the listener is safe, and the reason is worth stating because it looks
wrong at a glance: `setupWithNavController` installs **two** listeners — an
item-selected listener on the view, and an `OnDestinationChangedListener` on the
`NavController`. Only the first is replaced, so the bar still follows the
current destination on navigation and on back.

## Tabs

`menu/menu_bottom_nav.xml`, in order:

| Item | Destination |
| --- | --- |
| Home | `habitListFragment` |
| My habits | `allHabitsFragment` |
| + | action — opens the add/edit sheet |
| Settings | `settingsFragment` |

Menu item ids match destination ids, which is how `NavigationUI` binds them
without a mapping table. Progress is inserted before Settings in PR B.

## Back stack

Flat destinations, **not** a nested `<navigation>` graph per tab.
`onNavDestinationSelected` pops to the graph's start destination on each tab
switch, so switching tabs from a pushed screen does not leave it behind you.

Per-tab saved back stacks would require wrapping every tab in its own graph.
That is real structure for four shallow screens, and nothing in the mockup asks
for it. If a tab later grows a hierarchy worth preserving, this is the decision
to revisit.

## Bar visibility

The bar shows on tab roots only. The destination-changed listener hides it on
`habitDetailFragment` and on the add/edit sheet.

## Screen changes

- **`HabitListFragment`** — FAB removed; the archive and seed Snackbars, which
  anchor to it today, re-anchor to the bottom bar. The debug seeder item and the
  `SampleDataSeeder` injection move to Settings.
- **`AllHabitsFragment`** — the back-arrow navigation icon goes. It is a tab
  root now, not a pushed screen.
- **`menu_habit_list.xml`** — deleted. `action_all_habits` becomes a tab and
  `action_seed_sample_data` moves to Settings, which empties the menu.
- **`SettingsFragment`** (new) — toolbar plus one debug-only row for the seeder.
  Follows the existing screen conventions: `viewBinding` delegate, and a
  `BuildConfig.DEBUG` check so the seeder can never appear in a release build.

## Window insets

The fiddly part, and the most likely source of a visual bug. Each screen
currently pads its own bottom for the system bars. With a bar present, the tab
roots must pad for the **bar's** height while the bar itself consumes the system
inset. `habitDetailFragment` keeps its current behaviour, since the bar is
hidden there.

## Testing

No ViewModel changes, so the unit suite is untouched and should stay green
without edits — if it does not, something has moved that this design did not
intend.

One new instrumented test covers what only a real navigator can:

- selecting a tab changes the current destination;
- tapping `+` opens the add/edit sheet **and leaves the selected tab unchanged** —
  the single assertion that would catch the `+` being wired as a destination.

`HabitListNavigationTest` navigates programmatically and should still pass
unmodified.

Verified on the emulator screen by screen: every tab root, the bar hidden on
detail, no content trapped behind the bar, and the add sheet reachable from `+`.

---

# PR B — the Progress tab

Specified here for context; built after PR A lands.

- A fifth bottom-nav item, `progressFragment`, before Settings.
- One `StateFlow<ProgressScreenState>`, per the existing convention: shared
  fields on the data class, mutually exclusive situations in a sealed
  `ProgressDisplayState`, flat accessors for XML only.
- **Summary**: a ring showing `HabitStatistics.completionRate` for the current
  month across all active habits, plus a month calendar.
- **Habits**: the same month, one row per habit with its own rate, each row a
  way into the existing habit detail screen.
- The month is navigable, like the habit list's date bar.

The completion-rate arithmetic already exists in `HabitStatistics` and takes a
plain `Set<LocalDate>` per habit; aggregating across habits is a matter of
summing scheduled and completed day counts, not new arithmetic. Skips must stay
excluded from the denominator, exactly as they are everywhere else — the same
`skippedDates` argument the other callers pass.
