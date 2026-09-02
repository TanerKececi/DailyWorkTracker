# Progress Tab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Progress tab — a completion ring and month calendar for all habits combined, with a toggle to the same month broken down per habit — and close the deferred `SKIPPED` day status while doing it.

**Architecture:** One new screen following the established shape: a single `StateFlow<ProgressScreenState>` with a sealed `ProgressDisplayState`, flat accessors for XML only, and a Fragment that assigns `binding.state`. The month arithmetic goes in a new pure `util/ProgressSummary.kt` that *composes* `HabitStatistics` rather than changing it. Day-cell drawing is shared with the detail heatmap by moving `DayStatus` into `ui/common`.

**Tech Stack:** Kotlin, XML views with data binding, Material 3 (`CircularProgressIndicator`, `MaterialButtonToggleGroup`), RecyclerView + `GridLayoutManager`, Hilt, JUnit 4 + Turbine for unit tests, Espresso for instrumented.

**Spec:** `docs/superpowers/specs/2026-09-02-bottom-nav-and-progress-design.md`

## Global Constraints

- **Build command** (PowerShell, this machine only):
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:TEMP = "C:\gradle-tmp"; $env:TMP = "C:\gradle-tmp"; .\gradlew.bat :app:ktlintCheck :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon`
- **THE TRIPWIRE: `util/StreakCalculator.kt` and `util/HabitStatistics.kt` must not be modified by this plan.** Nothing here needs new arithmetic — only the existing functions called per habit and summed. If a task seems to need them changed, the model is wrong; stop and rethink.
- `connectedAndroidTest` takes no `--tests`; filter with `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`.
- `connectedDebugAndroidTest` leaves the app **uninstalled**; reinstall with `adb install -r app/build/outputs/apk/debug/app-debug.apk` before any manual check.
- Do not pipe gradle through `Select-String` — it mangles `$LASTEXITCODE`.
- **One `StateFlow<XxxScreenState>` per screen.** Shared fields on a data class; mutually exclusive situations in a sealed `XxxDisplayState`. Distinct situations get distinct types.
- **Flat accessors on state types exist for XML only.** Kotlin callers match on `displayState` directly.
- **`databinding-ktx` is disabled.** Fragments collect and assign `binding.state = it`; no StateFlow in XML, no two-way binding.
- **Bound widgets use `android:onClick`, never `OnCheckedChangeListener`** — the checked state is bound from state and a change listener echoes straight back.
- **Never put a Java-style cast in a layout**; never pass a bare `null` in a binding expression.
- **Emoji in data-bound layouts must be XML character references** (`&#x1F3C3;`).
- ktlint is enforced; a body expression that fits on the signature line must be on it.

### Decisions already taken

- The ring is **weighted by days due**: sum every habit's scheduled days for the month, sum every habit's completed days, divide once.
- A calendar cell has **three states — all / some / none** of the habits due that day.
- **`SKIPPED` is added to both surfaces**: the new calendar and the existing detail heatmap.

---

### Task 1: A skipped day stops reading as a miss

Closes the item deferred from the skipped-state feature. Independent and shippable on its own, and it must land before the Progress calendar so both screens share one vocabulary.

`DayStatus` moves to `ui/common` in the same task, because the Progress calendar will draw cells from it and a screen reaching into `ui.habitdetail` for a shared type is the wrong dependency.

**Files:**
- Create: `app/src/main/java/com/example/dailyworktracker/ui/common/DayStatus.kt`
- Modify: `app/src/main/java/com/example/dailyworktracker/ui/habitdetail/HeatmapItem.kt` (remove the enum, import it)
- Modify: `app/src/main/java/com/example/dailyworktracker/ui/habitdetail/HabitDetailViewModel.kt`
- Modify: `app/src/main/java/com/example/dailyworktracker/ui/habitdetail/HeatmapAdapter.kt`
- Modify: `app/src/main/java/com/example/dailyworktracker/ui/habitdetail/HabitDetailFragment.kt` (tint the third swatch)
- Modify: `app/src/main/res/layout/view_heatmap_legend.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/example/dailyworktracker/ui/habitdetail/HabitDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `HabitRepository.observeSkips(habitId): Flow<Set<LocalDate>>` (exists); `DayStatus` cases `COMPLETED`, `MISSED`, `NOT_SCHEDULED`, `PENDING`, `OUT_OF_RANGE`.
- Produces: `com.example.dailyworktracker.ui.common.DayStatus` with a new `SKIPPED` case; `HeatmapAdapter.skippedTint(context): Int`; view id `R.id.swatchSkipped`; string `R.string.habit_detail_legend_skipped`.

- [ ] **Step 1: Write the failing test**

Append to `HabitDetailViewModelTest.kt`, before the closing brace. It follows the existing `awaitDetail()` helper and the shape of `completed and missed days are distinguished`:

```kotlin
    @Test
    fun `a skipped day is marked skipped rather than missed`() =
        runTest {
            // The Records tiles already treat a skip as neutral. A red box under them saying the
            // opposite about the same day is the inconsistency this closes.
            repository.seed(habit(id = 1L, createdAt = 0L))
            repository.skipOn(1L, monday.minusDays(2))

            val day =
                awaitDetail().heatmap
                    .filterIsInstance<HeatmapItem.Day>()
                    .single { it.date == monday.minusDays(2) }

            assertEquals(DayStatus.SKIPPED, day.status)
        }
```

Add the import `com.example.dailyworktracker.ui.common.DayStatus` to the test file.

- [ ] **Step 2: Run it and confirm it fails**

Run: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:TEMP = "C:\gradle-tmp"; $env:TMP = "C:\gradle-tmp"; .\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "com.example.dailyworktracker.ui.habitdetail.HabitDetailViewModelTest"`

Expected: compile failure — `DayStatus.SKIPPED` and the `ui.common` package do not exist.

- [ ] **Step 3: Move the enum and add the case**

Create `app/src/main/java/com/example/dailyworktracker/ui/common/DayStatus.kt`:

```kotlin
package com.example.dailyworktracker.ui.common

/**
 * How a single day should be drawn in a calendar grid.
 *
 * Shared by the detail heatmap, which shows one habit, and the Progress calendar, which shows every
 * habit at once - so a day means the same thing on both screens.
 */
enum class DayStatus {
    /** Due that day and done: a ticked box. */
    COMPLETED,

    /** Due that day and missed: an empty box. Only assigned to days that have already resolved. */
    MISSED,

    /**
     * Deliberately skipped.
     *
     * Neutral, like a day the habit was never due: it does not break a streak and it leaves the
     * completion-rate denominator. Drawn distinctly from [MISSED] so the grid agrees with the
     * numbers above it.
     */
    SKIPPED,

    /** The habit does not repeat on that weekday, so no box is drawn at all. */
    NOT_SCHEDULED,

    /** Due today and not done yet. Distinct from [MISSED]: the day has not resolved. */
    PENDING,

    /** Before the habit existed, or later than today. Drawn as an empty slot. */
    OUT_OF_RANGE,
}
```

Delete the `enum class DayStatus { ... }` block from `HeatmapItem.kt` and add `import com.example.dailyworktracker.ui.common.DayStatus` there. Add the same import to `HabitDetailViewModel.kt` and `HeatmapAdapter.kt`.

- [ ] **Step 4: Map skipped days in the ViewModel**

`buildState` already receives `skipped: Set<LocalDate>`. Thread it through to the grid. In `buildState`, change the heatmap line to:

```kotlin
                heatmap = buildHeatmap(habit, completions, skipped, createdOn, today),
```

Change `buildHeatmap`'s signature and its `statusOf` call:

```kotlin
        private fun buildHeatmap(
            habit: Habit,
            completions: Map<LocalDate, Int?>,
            skipped: Set<LocalDate>,
            createdOn: LocalDate,
            today: LocalDate,
        ): List<HeatmapItem> {
```

```kotlin
                            status = statusOf(habit, completed, skipped, date, createdOn, today),
```

And `statusOf` — the skip test goes **before** the schedule test, so a skipped day reads as skipped rather than being swallowed by a weekday it was not due on... no: it goes **after** `NOT_SCHEDULED`, because a day the habit never repeats on was not skipped in any meaningful sense, and before `COMPLETED`, which cannot co-occur with a skip anyway:

```kotlin
        private fun statusOf(
            habit: Habit,
            completed: Set<LocalDate>,
            skipped: Set<LocalDate>,
            date: LocalDate,
            createdOn: LocalDate,
            today: LocalDate,
        ): DayStatus =
            when {
                date.isAfter(today) || date.isBefore(createdOn) -> DayStatus.OUT_OF_RANGE
                !WeekdaySchedule.isScheduledOn(habit.scheduleDaysBitmask, date.dayOfWeek) ->
                    DayStatus.NOT_SCHEDULED

                // Before COMPLETED only for symmetry with the repository's rule; the two are
                // mutually exclusive there, so the order cannot actually matter.
                date in skipped -> DayStatus.SKIPPED
                date in completed -> DayStatus.COMPLETED
                // Today has not resolved yet, so it is neither a miss nor an off-day.
                date == today -> DayStatus.PENDING
                else -> DayStatus.MISSED
            }
```

- [ ] **Step 5: Draw it**

In `HeatmapAdapter.applyBox`, add a branch before the `MISSED, OUT_OF_RANGE` one:

```kotlin
                // A deliberate rest day: filled, but in a muted tone, so it reads as "handled"
                // rather than as either a win or a miss.
                DayStatus.SKIPPED -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_filled)
                    box.backgroundTintList = ColorStateList.valueOf(attrColor(context, OUTLINE))
                }
```

In `textColor`, add `DayStatus.SKIPPED -> attrColor(context, ON_SURFACE_VARIANT)` — it already falls through to that via `else`, so only add a case if the `when` is made exhaustive; otherwise leave it.

In `describe`, add `DayStatus.SKIPPED -> context.getString(R.string.habit_detail_legend_skipped)` so a screen reader does not call a rest day a miss.

Beside the existing `completedTint` / `missedTint` helpers in the companion object, add:

```kotlin
        /** The muted fill a skipped day is drawn with; the legend reuses it. */
        fun skippedTint(context: Context): Int = attrColor(context, OUTLINE)
```

- [ ] **Step 6: Add the third legend entry**

In `strings.xml`, beside the other two legend strings:

```xml
    <string name="habit_detail_legend_skipped">Skipped</string>
```

In `view_heatmap_legend.xml`, after the missed label, add a third swatch and label copying the shape of the missed pair, with `android:id="@+id/swatchSkipped"`, `android:background="@drawable/bg_heatmap_day_filled"`, `android:layout_marginStart="16dp"`, and a label with `android:text="@string/habit_detail_legend_skipped"` and `android:id="@+id/labelSkipped"`.

In `HabitDetailFragment.tintLegend()`, add:

```kotlin
        binding.legendHeatmap.swatchSkipped.backgroundTintList =
            ColorStateList.valueOf(HeatmapAdapter.skippedTint(context))
```

- [ ] **Step 7: Run the test and confirm it passes**

Run the unit command from Step 2. Expected: PASS, and every other `HabitDetailViewModelTest` case still green.

- [ ] **Step 8: Prove it has teeth**

Temporarily delete the `date in skipped -> DayStatus.SKIPPED` line from `statusOf` and re-run. Expected: the new test fails asserting `SKIPPED` but finding `MISSED`. Restore the line.

- [ ] **Step 9: Full suite and commit**

Run the full build command. Then:

```bash
git add -A
git commit -m "feat: show a skipped day as skipped, not missed"
```

---

### Task 2: The repository serves every habit's history at once

The Progress screen needs completions and skips for *all* habits. Both DAOs already have the queries; only the repository boundary is missing.

**Files:**
- Modify: `app/src/main/java/com/example/dailyworktracker/data/repository/HabitRepository.kt`
- Modify: `app/src/main/java/com/example/dailyworktracker/data/repository/HabitRepositoryImpl.kt`
- Modify: `app/src/test/java/com/example/dailyworktracker/fake/FakeHabitRepository.kt`
- Test: `app/src/test/java/com/example/dailyworktracker/fake/FakeHabitRepositorySkipTest.kt`

**Interfaces:**
- Consumes: `HabitCompletionDao.observeAllCompletions()`, `HabitSkipDao.observeAllSkips()` (both exist).
- Produces: `HabitRepository.observeAllCompletionDates(): Flow<Map<Long, Set<LocalDate>>>` and `observeAllSkipDates(): Flow<Map<Long, Set<LocalDate>>>`, both keyed by habit id, both omitting habits with no rows.

- [ ] **Step 1: Write the failing test**

Append to `FakeHabitRepositorySkipTest.kt`:

```kotlin
    @Test
    fun `history for every habit arrives keyed by habit`() =
        runTest {
            // The Progress screen needs one emission covering all habits, not a query per habit.
            repository.seed(habit(id = 1L), habit(id = 2L))
            repository.completeOn(1L, date)
            repository.skipOn(2L, date)

            assertEquals(mapOf(1L to setOf(date)), repository.observeAllCompletionDates().first())
            assertEquals(mapOf(2L to setOf(date)), repository.observeAllSkipDates().first())
        }
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `... .\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "com.example.dailyworktracker.fake.FakeHabitRepositorySkipTest"`

Expected: compile failure — neither method exists.

- [ ] **Step 3: Add them to the interface**

In `HabitRepository.kt`, after `observeSkips`:

```kotlin
    /**
     * Completed days for every habit at once, keyed by habit id.
     *
     * The Progress screen judges a whole month across every habit, so a query per habit would mean
     * a flow per habit and a combine that changes shape whenever a habit is added. Personal habit
     * histories stay small enough that one query grouped in memory is cheaper.
     */
    fun observeAllCompletionDates(): Flow<Map<Long, Set<LocalDate>>>

    /** Skipped days for every habit at once, keyed by habit id. Same reasoning as above. */
    fun observeAllSkipDates(): Flow<Map<Long, Set<LocalDate>>>
```

- [ ] **Step 4: Implement them**

In `HabitRepositoryImpl.kt`, after `observeSkips`. Both reuse the grouping already done inline in `observeHabitsFor`:

```kotlin
        override fun observeAllCompletionDates(): Flow<Map<Long, Set<LocalDate>>> =
            completionDao.observeAllCompletions().map { rows -> groupByHabit(rows.map { it.habitId to it.date }) }

        override fun observeAllSkipDates(): Flow<Map<Long, Set<LocalDate>>> =
            skipDao.observeAllSkips().map { rows -> groupByHabit(rows.map { it.habitId to it.date }) }

        /** Epoch days become LocalDates at this boundary, as every other date does. */
        private fun groupByHabit(rows: List<Pair<Long, Long>>): Map<Long, Set<LocalDate>> =
            rows.groupBy({ it.first }, { LocalDate.ofEpochDay(it.second) })
                .mapValues { (_, dates) -> dates.toSet() }
```

- [ ] **Step 5: Implement them on the fake**

In `FakeHabitRepository.kt`, beside `observeSkips`:

```kotlin
    override fun observeAllCompletionDates(): Flow<Map<Long, Set<LocalDate>>> =
        completions.map { all -> all.groupBy({ it.habitId }, { it.date }).mapValues { it.value.toSet() } }

    override fun observeAllSkipDates(): Flow<Map<Long, Set<LocalDate>>> =
        skips.map { all -> all.groupBy({ it.habitId }, { it.date }).mapValues { it.value.toSet() } }
```

- [ ] **Step 6: Run the test and confirm it passes, then commit**

Run the unit command from Step 2, then the full build command.

```bash
git add -A
git commit -m "feat: expose every habit's history in one emission"
```

---

### Task 3: The month arithmetic, as a pure function

All of the Progress screen's numbers, with no Android and no clock. `HabitStatistics` is **called, never changed** — this is composition over the tripwire, not a modification of it.

**Files:**
- Create: `app/src/main/java/com/example/dailyworktracker/util/ProgressSummary.kt`
- Test: `app/src/test/java/com/example/dailyworktracker/util/ProgressSummaryTest.kt`

**Interfaces:**
- Consumes: `HabitStatistics.scheduledDayCount(completedDates, scheduleDaysBitmask, from, to, skippedDates)` and `completedDayCount(...)` with the same parameters; `WeekdaySchedule.isScheduledOn(bitmask, dayOfWeek)`; `com.example.dailyworktracker.ui.common.DayStatus`.
- Produces:
  - `data class HabitMonth(val habitId: Long, val scheduleDaysBitmask: Int, val createdOn: LocalDate, val completed: Set<LocalDate>, val skipped: Set<LocalDate>)`
  - `ProgressSummary.completionRate(habits: List<HabitMonth>, month: YearMonth, today: LocalDate): Float`
  - `ProgressSummary.rateFor(habit: HabitMonth, month: YearMonth, today: LocalDate): Float`
  - `ProgressSummary.dayStatus(habits: List<HabitMonth>, date: LocalDate, today: LocalDate): DayStatus`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/example/dailyworktracker/util/ProgressSummaryTest.kt`:

```kotlin
package com.example.dailyworktracker.util

import com.example.dailyworktracker.ui.common.DayStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class ProgressSummaryTest {
    private val august = YearMonth.of(2026, 8)
    private val monday = LocalDate.of(2026, 8, 31)

    private fun habitMonth(
        id: Long = 1L,
        schedule: Int = WeekdaySchedule.EVERY_DAY,
        createdOn: LocalDate = LocalDate.of(2026, 1, 1),
        completed: Set<LocalDate> = emptySet(),
        skipped: Set<LocalDate> = emptySet(),
    ) = HabitMonth(id, schedule, createdOn, completed, skipped)

    @Test
    fun `the rate weighs a habit by how often it was due`() {
        // A daily habit kept 0 of 30 days, and a Monday-only habit kept all 5 of its Mondays.
        // Weighted that is 5 of 35, not the 50% an average of the two rates would report.
        val mondays = (1..31).map { LocalDate.of(2026, 8, it) }
            .filter { it.dayOfWeek == DayOfWeek.MONDAY }
            .toSet()
        val habits =
            listOf(
                habitMonth(id = 1L),
                habitMonth(
                    id = 2L,
                    schedule = WeekdaySchedule.toBitmask(listOf(DayOfWeek.MONDAY)),
                    completed = mondays,
                ),
            )

        val rate = ProgressSummary.completionRate(habits, august, today = monday)

        // 31 daily days + 5 Mondays, minus the unresolved 31st for the daily habit.
        assertEquals(5f / 35f, rate, 0.001f)
    }

    @Test
    fun `days after today do not count against the rate`() {
        // Judged mid-month, the rest of the month has not happened yet.
        val midMonth = LocalDate.of(2026, 8, 10)
        val kept = (1..10).map { LocalDate.of(2026, 8, it) }.toSet()

        val rate = ProgressSummary.completionRate(
            listOf(habitMonth(completed = kept)),
            august,
            today = midMonth,
        )

        assertEquals(1f, rate, 0.001f)
    }

    @Test
    fun `a habit created mid-month is not judged on the days before it existed`() {
        val createdOn = LocalDate.of(2026, 8, 20)
        val kept = (20..31).map { LocalDate.of(2026, 8, it) }.toSet()

        val rate = ProgressSummary.completionRate(
            listOf(habitMonth(createdOn = createdOn, completed = kept)),
            august,
            today = monday,
        )

        assertEquals(1f, rate, 0.001f)
    }

    @Test
    fun `a skipped day leaves the denominator here too`() {
        val kept = (1..30).map { LocalDate.of(2026, 8, it) }.toSet()

        val rate = ProgressSummary.completionRate(
            listOf(habitMonth(completed = kept, skipped = setOf(monday))),
            august,
            today = monday,
        )

        assertEquals(1f, rate, 0.001f)
    }

    @Test
    fun `a day everything due was done reads as completed`() {
        val date = LocalDate.of(2026, 8, 10)
        val habits = listOf(habitMonth(id = 1L, completed = setOf(date)), habitMonth(id = 2L, completed = setOf(date)))

        assertEquals(DayStatus.COMPLETED, ProgressSummary.dayStatus(habits, date, today = monday))
    }

    @Test
    fun `a day some of it was done is distinct from a day none of it was`() {
        // Four habits kept out of five must not look like a day nothing happened.
        val date = LocalDate.of(2026, 8, 10)
        val partial = listOf(habitMonth(id = 1L, completed = setOf(date)), habitMonth(id = 2L))
        val none = listOf(habitMonth(id = 1L), habitMonth(id = 2L))

        assertEquals(DayStatus.PARTIAL, ProgressSummary.dayStatus(partial, date, today = monday))
        assertEquals(DayStatus.MISSED, ProgressSummary.dayStatus(none, date, today = monday))
    }

    @Test
    fun `a day nothing was due is not scheduled`() {
        val tuesday = LocalDate.of(2026, 8, 11)
        val mondayOnly = WeekdaySchedule.toBitmask(listOf(DayOfWeek.MONDAY))

        assertEquals(
            DayStatus.NOT_SCHEDULED,
            ProgressSummary.dayStatus(listOf(habitMonth(schedule = mondayOnly)), tuesday, today = monday),
        )
    }

    @Test
    fun `a skipped day does not drag the calendar down`() {
        // The one habit due was skipped, so nothing was owed: neutral, not a miss.
        val date = LocalDate.of(2026, 8, 10)

        assertEquals(
            DayStatus.SKIPPED,
            ProgressSummary.dayStatus(listOf(habitMonth(skipped = setOf(date))), date, today = monday),
        )
    }

    @Test
    fun `today is pending while it is still unresolved, and the future is out of range`() {
        assertEquals(
            DayStatus.PENDING,
            ProgressSummary.dayStatus(listOf(habitMonth()), monday, today = monday),
        )
        assertEquals(
            DayStatus.OUT_OF_RANGE,
            ProgressSummary.dayStatus(listOf(habitMonth()), monday.plusDays(1), today = monday),
        )
    }
}
```

- [ ] **Step 2: Run them and confirm they fail**

Run: `... .\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "com.example.dailyworktracker.util.ProgressSummaryTest"`

Expected: compile failure — `HabitMonth`, `ProgressSummary` and `DayStatus.PARTIAL` do not exist.

- [ ] **Step 3: Add PARTIAL to DayStatus**

In `ui/common/DayStatus.kt`, after `COMPLETED`:

```kotlin
    /**
     * Some but not all of the habits due that day were done.
     *
     * Only the Progress calendar produces this: a single habit's day is done or it is not. Without
     * it a day four habits out of five were kept on would look like a day nothing happened.
     */
    PARTIAL,
```

- [ ] **Step 4: Write the implementation**

Create `app/src/main/java/com/example/dailyworktracker/util/ProgressSummary.kt`:

```kotlin
package com.example.dailyworktracker.util

import com.example.dailyworktracker.ui.common.DayStatus
import java.time.LocalDate
import java.time.YearMonth

/**
 * One habit's month, reduced to what the arithmetic needs.
 *
 * Carries dates rather than rows: like [StreakCalculator], nothing here cares how much was logged,
 * only which days have a record.
 */
data class HabitMonth(
    val habitId: Long,
    val scheduleDaysBitmask: Int,
    val createdOn: LocalDate,
    val completed: Set<LocalDate>,
    val skipped: Set<LocalDate>,
)

/**
 * The Progress screen's numbers, for a whole month across every habit.
 *
 * Deliberately built *on* [HabitStatistics] rather than beside it: one habit's rate is already
 * defined there, and re-deriving it here would let the two disagree about what a skipped day or an
 * unresolved final day means. This object only decides how several habits combine.
 */
object ProgressSummary {
    /**
     * Share of everything due in [month] that was done, in `0f..1f`.
     *
     * Weighted by how often each habit was due: the numerator and denominator are summed across
     * habits before dividing once. A habit due daily therefore counts for more than one due on
     * Mondays, which is what a single headline percentage has to mean - an average of per-habit
     * rates would let a weekly habit you kept hide a daily one you missed all month.
     */
    fun completionRate(
        habits: List<HabitMonth>,
        month: YearMonth,
        today: LocalDate,
    ): Float {
        var scheduled = 0
        var completed = 0
        habits.forEach { habit ->
            val from = maxOf(month.atDay(1), habit.createdOn)
            // The month runs no further than today: days that have not happened are not misses.
            val to = minOf(month.atEndOfMonth(), today)
            if (from.isAfter(to)) return@forEach

            scheduled +=
                HabitStatistics.scheduledDayCount(
                    habit.completed,
                    habit.scheduleDaysBitmask,
                    from,
                    to,
                    habit.skipped,
                )
            completed +=
                HabitStatistics.completedDayCount(
                    habit.completed,
                    habit.scheduleDaysBitmask,
                    from,
                    to,
                    habit.skipped,
                )
        }
        // Nothing due yet reads better as 0 than as an undefined rate, matching HabitStatistics.
        return if (scheduled == 0) 0f else completed.toFloat() / scheduled
    }

    /** One habit's own rate for [month], for the per-habit breakdown. */
    fun rateFor(
        habit: HabitMonth,
        month: YearMonth,
        today: LocalDate,
    ): Float = completionRate(listOf(habit), month, today)

    /**
     * How one calendar cell should be drawn, given every habit that was due that day.
     *
     * A day is judged only on the habits that actually owed something: one that does not repeat
     * that weekday, did not exist yet, or was deliberately skipped is left out of the count
     * entirely, exactly as it is left out of the rate.
     */
    fun dayStatus(
        habits: List<HabitMonth>,
        date: LocalDate,
        today: LocalDate,
    ): DayStatus {
        if (date.isAfter(today)) return DayStatus.OUT_OF_RANGE

        val due =
            habits.filter { habit ->
                !date.isBefore(habit.createdOn) &&
                    WeekdaySchedule.isScheduledOn(habit.scheduleDaysBitmask, date.dayOfWeek) &&
                    date !in habit.skipped
            }

        if (due.isEmpty()) {
            // Nothing was owed. Skipped only when a habit was actually due and passed on, so a day
            // off reads differently from a day the schedule never touched.
            val wasSkipped =
                habits.any { habit ->
                    date in habit.skipped &&
                        WeekdaySchedule.isScheduledOn(habit.scheduleDaysBitmask, date.dayOfWeek)
                }
            return if (wasSkipped) DayStatus.SKIPPED else DayStatus.NOT_SCHEDULED
        }

        val done = due.count { date in it.completed }
        return when {
            done == due.size -> DayStatus.COMPLETED
            done > 0 -> DayStatus.PARTIAL
            // Today has not resolved yet, so an untouched today is not a miss.
            date == today -> DayStatus.PENDING
            else -> DayStatus.MISSED
        }
    }
}
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run the unit command from Step 2. Expected: all nine pass.

- [ ] **Step 6: Prove the weighting test has teeth**

Temporarily change `completionRate` to average per-habit rates instead — replace the body with
`return if (habits.isEmpty()) 0f else habits.map { rateFor(it, month, today) }.average().toFloat()`
(and stub `rateFor` to the summing version so it still compiles). Re-run.

Expected: `the rate weighs a habit by how often it was due` fails, reporting 0.5 against an expected 0.142. Restore the summing version.

- [ ] **Step 7: Confirm the tripwire is untouched, then commit**

```bash
git diff --stat master -- app/src/main/java/com/example/dailyworktracker/util/StreakCalculator.kt app/src/main/java/com/example/dailyworktracker/util/HabitStatistics.kt
```

Expected: **empty output.** If either file appears, stop — this plan composes them and must not change them.

```bash
git add -A
git commit -m "feat: work out a whole month's progress across every habit"
```

---

### Task 4: The Progress screen's state and ViewModel

**Files:**
- Create: `app/src/main/java/com/example/dailyworktracker/ui/progress/ProgressScreenState.kt`
- Create: `app/src/main/java/com/example/dailyworktracker/ui/progress/ProgressViewModel.kt`
- Create: `app/src/main/java/com/example/dailyworktracker/ui/progress/ProgressHabitUiModel.kt`
- Create: `app/src/main/java/com/example/dailyworktracker/ui/progress/CalendarDay.kt`
- Test: `app/src/test/java/com/example/dailyworktracker/ui/progress/ProgressViewModelTest.kt`

**Interfaces:**
- Consumes: `HabitRepository.observeAllHabits()`, `observeAllCompletionDates()`, `observeAllSkipDates()` (Task 2); `ProgressSummary`, `HabitMonth` (Task 3); `DateProvider.today()`; `HabitVisibility.createdDate(habit)`.
- Produces:
  - `enum class ProgressMode { SUMMARY, HABITS }`
  - `data class CalendarDay(val date: LocalDate?, val status: DayStatus)` — a null date is a leading blank before the 1st.
  - `data class ProgressHabitUiModel(val id: Long, val title: String, val emoji: String, val rate: Float)` with `val percent: Int get() = (rate * 100).roundToInt()`
  - `ProgressScreenState(month, today, mode, displayState)` with flat accessors and `sealed interface ProgressDisplayState { Loading, Content(rate, days, habits), Empty, Error }`
  - `ProgressViewModel.onModeSelected(ProgressMode)`, `onPreviousMonthClicked()`, `onNextMonthClicked()`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/dailyworktracker/ui/progress/ProgressViewModelTest.kt`:

```kotlin
package com.example.dailyworktracker.ui.progress

import app.cash.turbine.test
import com.example.dailyworktracker.fake.FakeDateProvider
import com.example.dailyworktracker.fake.FakeHabitRepository
import com.example.dailyworktracker.fake.habit
import com.example.dailyworktracker.testing.MainDispatcherRule
import com.example.dailyworktracker.ui.common.DayStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class ProgressViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val monday = FakeDateProvider.MONDAY
    private val repository = FakeHabitRepository()
    private val dateProvider = FakeDateProvider()

    private fun viewModel() = ProgressViewModel(repository, dateProvider)

    @Test
    fun `starts on the current month`() =
        runTest {
            repository.seed(habit(id = 1L, createdAt = 0L))

            viewModel().uiState.test {
                assertEquals(ProgressDisplayState.Loading, awaitItem().displayState)
                assertEquals(YearMonth.from(monday), awaitItem().month)
            }
        }

    @Test
    fun `reports the weighted rate for the month`() =
        runTest {
            repository.seed(habit(id = 1L, createdAt = 0L))
            repository.completeOn(1L, monday.minusDays(1), monday.minusDays(2))

            viewModel().uiState.test {
                awaitItem()
                val content = awaitItem().displayState as ProgressDisplayState.Content
                // Two kept days out of the month's resolved days so far; the exact figure is
                // ProgressSummary's business, so assert only that the screen reports what it says.
                assertEquals(
                    com.example.dailyworktracker.util.ProgressSummary.completionRate(
                        listOf(
                            com.example.dailyworktracker.util.HabitMonth(
                                habitId = 1L,
                                scheduleDaysBitmask = com.example.dailyworktracker.util.WeekdaySchedule.EVERY_DAY,
                                createdOn = java.time.LocalDate.ofEpochDay(0),
                                completed = setOf(monday.minusDays(1), monday.minusDays(2)),
                                skipped = emptySet(),
                            ),
                        ),
                        YearMonth.from(monday),
                        monday,
                    ),
                    content.rate,
                    0.001f,
                )
            }
        }

    @Test
    fun `the calendar starts on the weekday the month actually starts on`() =
        runTest {
            // Without the leading blanks every date would sit under the wrong weekday column.
            repository.seed(habit(id = 1L, createdAt = 0L))

            viewModel().uiState.test {
                awaitItem()
                val days = (awaitItem().displayState as ProgressDisplayState.Content).days
                val firstOfMonth = YearMonth.from(monday).atDay(1)
                val blanks = days.takeWhile { it.date == null }.size

                assertEquals(firstOfMonth.dayOfWeek.value - 1, blanks)
                assertEquals(firstOfMonth, days[blanks].date)
            }
        }

    @Test
    fun `stepping back a month changes the month without changing today`() =
        runTest {
            repository.seed(habit(id = 1L, createdAt = 0L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                awaitItem()

                viewModel.onPreviousMonthClicked()

                val state = awaitItem()
                assertEquals(YearMonth.from(monday).minusMonths(1), state.month)
                assertEquals(monday, state.today)
            }
        }

    @Test
    fun `the next month is out of reach while it has not happened`() =
        runTest {
            repository.seed(habit(id = 1L, createdAt = 0L))
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                assertEquals(false, awaitItem().canGoToNextMonth)

                viewModel.onPreviousMonthClicked()

                assertEquals(true, awaitItem().canGoToNextMonth)
            }
        }

    @Test
    fun `the habits mode lists each habit with its own rate`() =
        runTest {
            repository.seed(
                habit(id = 1L, title = "Brush teeth", createdAt = 0L),
                habit(id = 2L, title = "Read", createdAt = 0L),
            )
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                awaitItem()

                viewModel.onModeSelected(ProgressMode.HABITS)

                val state = awaitItem()
                assertEquals(ProgressMode.HABITS, state.mode)
                assertEquals(
                    listOf("Brush teeth", "Read"),
                    (state.displayState as ProgressDisplayState.Content).habits.map { it.title },
                )
            }
        }

    @Test
    fun `an archived habit is left out entirely`() =
        runTest {
            // Progress is about what you are keeping now, not what you used to.
            repository.seed(habit(id = 1L, createdAt = 0L), habit(id = 2L, createdAt = 0L))
            repository.archiveHabit(2L)

            viewModel().uiState.test {
                awaitItem()
                val content = awaitItem().displayState as ProgressDisplayState.Content
                assertEquals(1, content.habits.size)
            }
        }

    @Test
    fun `no habits at all is an empty screen, not a zero percent one`() =
        runTest {
            viewModel().uiState.test {
                assertEquals(ProgressDisplayState.Loading, awaitItem().displayState)
                assertEquals(ProgressDisplayState.Empty, awaitItem().displayState)
            }
        }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `... .\gradlew.bat :app:testDebugUnitTest --no-daemon --tests "com.example.dailyworktracker.ui.progress.ProgressViewModelTest"`

Expected: compile failure — nothing in `ui.progress` exists yet.

- [ ] **Step 3: Create the small models**

`ui/progress/CalendarDay.kt`:

```kotlin
package com.example.dailyworktracker.ui.progress

import com.example.dailyworktracker.ui.common.DayStatus
import java.time.LocalDate

/**
 * One cell of the month grid.
 *
 * [date] is null for the blank slots before the 1st. They are real items rather than an offset the
 * view works out, so the grid can be a plain seven-column list and every date lands under its own
 * weekday without span arithmetic.
 */
data class CalendarDay(
    val date: LocalDate?,
    val status: DayStatus,
)
```

`ui/progress/ProgressHabitUiModel.kt`:

```kotlin
package com.example.dailyworktracker.ui.progress

import kotlin.math.roundToInt

/** One row of the per-habit breakdown, already reduced to what the view draws. */
data class ProgressHabitUiModel(
    val id: Long,
    val title: String,
    val emoji: String,
    val rate: Float,
) {
    /** Rounded once here, so the row and any label of it cannot disagree. */
    val percent: Int get() = (rate * 100).roundToInt()
}
```

- [ ] **Step 4: Create the screen state**

`ui/progress/ProgressScreenState.kt`:

```kotlin
package com.example.dailyworktracker.ui.progress

import androidx.annotation.StringRes
import com.example.dailyworktracker.R
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/** Which half of the toggle is showing. */
enum class ProgressMode {
    SUMMARY,
    HABITS,
}

/**
 * Everything the Progress screen draws.
 *
 * Flat accessors below exist for XML only. Binding expressions have no `when` and no smart-casting,
 * so without these the layout would need Java-style casts. Kotlin callers - ViewModel, tests -
 * match on [displayState] directly instead.
 */
data class ProgressScreenState(
    val month: YearMonth,
    val today: LocalDate,
    val mode: ProgressMode,
    val displayState: ProgressDisplayState,
) {
    /** A month that has not happened yet has nothing to show, so the stepper stops at this one. */
    val canGoToNextMonth: Boolean get() = month.isBefore(YearMonth.from(today))

    val isSummary: Boolean get() = mode == ProgressMode.SUMMARY
    val isHabits: Boolean get() = mode == ProgressMode.HABITS

    // flat accessors for XML only
    val isLoading: Boolean get() = displayState is ProgressDisplayState.Loading
    val isContent: Boolean get() = displayState is ProgressDisplayState.Content
    val isEmpty: Boolean get() = displayState is ProgressDisplayState.Empty
    val isError: Boolean get() = displayState is ProgressDisplayState.Error

    /** Summary shows the ring and the calendar; Habits shows the breakdown. Never both. */
    val showsCalendar: Boolean get() = isContent && isSummary
    val showsHabits: Boolean get() = isContent && isHabits

    val rate: Float get() = (displayState as? ProgressDisplayState.Content)?.rate ?: 0f
    val percent: Int get() = (rate * 100).roundToInt()
    val days: List<CalendarDay> get() = displayState.days
    val habits: List<ProgressHabitUiModel> get() = displayState.habits

    val errorMessage: String?
        get() = (displayState as? ProgressDisplayState.Error)?.throwable?.localizedMessage

    @get:StringRes
    val emptyTitleRes: Int get() = R.string.progress_empty_title

    @get:StringRes
    val emptyMessageRes: Int get() = R.string.progress_empty_message
}

sealed interface ProgressDisplayState {
    data object Loading : ProgressDisplayState

    data class Content(
        val rate: Float,
        override val days: List<CalendarDay>,
        override val habits: List<ProgressHabitUiModel>,
    ) : ProgressDisplayState

    /** No active habits at all. Distinct from a real 0%, which means habits exist and were missed. */
    data object Empty : ProgressDisplayState

    data class Error(val throwable: Throwable) : ProgressDisplayState

    /** Empty in every state but Content, so the lists can bind without the layout branching. */
    val days: List<CalendarDay> get() = emptyList()

    val habits: List<ProgressHabitUiModel> get() = emptyList()
}
```

- [ ] **Step 5: Create the ViewModel**

`ui/progress/ProgressViewModel.kt`:

```kotlin
package com.example.dailyworktracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.util.DateProvider
import com.example.dailyworktracker.util.HabitMonth
import com.example.dailyworktracker.util.HabitVisibility
import com.example.dailyworktracker.util.ProgressSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * How every habit is going over one month.
 *
 * The arithmetic all lives in [ProgressSummary]; this only decides which month is on screen and
 * turns stored rows into the shapes the view draws.
 */
@HiltViewModel
class ProgressViewModel
    @Inject
    constructor(
        repository: HabitRepository,
        private val dateProvider: DateProvider,
    ) : ViewModel() {
        private val month = MutableStateFlow(YearMonth.from(dateProvider.today()))
        private val mode = MutableStateFlow(ProgressMode.SUMMARY)

        val uiState: StateFlow<ProgressScreenState> =
            combine(
                repository.observeAllHabits(),
                repository.observeAllCompletionDates(),
                repository.observeAllSkipDates(),
                month,
                mode,
            ) { habits, completions, skips, shownMonth, shownMode ->
                val today = dateProvider.today()
                // Progress is about what is being kept now, so an archived habit is not judged.
                val active = habits.filterNot { it.isArchived }
                val months = active.map { it.toHabitMonth(completions, skips) }

                ProgressScreenState(
                    month = shownMonth,
                    today = today,
                    mode = shownMode,
                    displayState =
                        if (active.isEmpty()) {
                            ProgressDisplayState.Empty
                        } else {
                            ProgressDisplayState.Content(
                                rate = ProgressSummary.completionRate(months, shownMonth, today),
                                days = calendarDays(months, shownMonth, today),
                                habits = active.zip(months) { habit, habitMonth ->
                                    ProgressHabitUiModel(
                                        id = habit.id,
                                        title = habit.title,
                                        emoji = habit.emoji,
                                        rate = ProgressSummary.rateFor(habitMonth, shownMonth, today),
                                    )
                                },
                            )
                        },
                )
            }
                .catch { throwable ->
                    emit(
                        ProgressScreenState(
                            month = month.value,
                            today = dateProvider.today(),
                            mode = mode.value,
                            displayState = ProgressDisplayState.Error(throwable),
                        ),
                    )
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue =
                        ProgressScreenState(
                            month = YearMonth.from(dateProvider.today()),
                            today = dateProvider.today(),
                            mode = ProgressMode.SUMMARY,
                            displayState = ProgressDisplayState.Loading,
                        ),
                )

        fun onModeSelected(selected: ProgressMode) {
            mode.value = selected
        }

        fun onPreviousMonthClicked() {
            month.value = month.value.minusMonths(1)
        }

        /** Guarded as well as hidden: a month that has not happened has nothing to show. */
        fun onNextMonthClicked() {
            val next = month.value.plusMonths(1)
            if (!next.isAfter(YearMonth.from(dateProvider.today()))) month.value = next
        }

        private fun Habit.toHabitMonth(
            completions: Map<Long, Set<LocalDate>>,
            skips: Map<Long, Set<LocalDate>>,
        ) = HabitMonth(
            habitId = id,
            scheduleDaysBitmask = scheduleDaysBitmask,
            createdOn = HabitVisibility.createdDate(this),
            completed = completions[id].orEmpty(),
            skipped = skips[id].orEmpty(),
        )

        /**
         * The month as a seven-column grid.
         *
         * Leading blanks are real items so every date lands under its own weekday. Trailing blanks
         * are not needed: the grid simply ends.
         */
        private fun calendarDays(
            habits: List<HabitMonth>,
            shownMonth: YearMonth,
            today: LocalDate,
        ): List<CalendarDay> {
            val firstOfMonth = shownMonth.atDay(1)
            val leadingBlanks = firstOfMonth.dayOfWeek.value - 1

            return List(leadingBlanks) { CalendarDay(date = null, status = DayStatus.OUT_OF_RANGE) } +
                (1..shownMonth.lengthOfMonth()).map { dayOfMonth ->
                    val date = shownMonth.atDay(dayOfMonth)
                    CalendarDay(date, ProgressSummary.dayStatus(habits, date, today))
                }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
```

Add `import com.example.dailyworktracker.ui.common.DayStatus` to the file.

- [ ] **Step 6: Add the empty-state strings**

In `strings.xml`, in a new block after the Settings one:

```xml
    <!-- Progress -->
    <string name="progress_title">Progress</string>
    <string name="progress_empty_title">Nothing to measure yet</string>
    <string name="progress_empty_message">Add a habit and your progress will show up here.</string>
    <string name="progress_mode_summary">Summary</string>
    <string name="progress_mode_habits">Habits</string>
    <string name="progress_rate_label">of everything due this month</string>
    <string name="progress_previous_month">Previous month</string>
    <string name="progress_next_month">Next month</string>
    <string name="progress_habit_rate">%1$d%%</string>
    <string name="nav_progress">Progress</string>
```

- [ ] **Step 7: Run the tests and confirm they pass, then commit**

Run the unit command from Step 2, then the full build command.

```bash
git add -A
git commit -m "feat: model a month of progress across every habit"
```

---

### Task 5: The Progress screen itself

**Files:**
- Create: `app/src/main/java/com/example/dailyworktracker/ui/progress/ProgressFragment.kt`
- Create: `app/src/main/java/com/example/dailyworktracker/ui/progress/CalendarAdapter.kt`
- Create: `app/src/main/java/com/example/dailyworktracker/ui/progress/ProgressHabitAdapter.kt`
- Create: `app/src/main/res/layout/fragment_progress.xml`
- Create: `app/src/main/res/layout/item_calendar_day.xml`
- Create: `app/src/main/res/layout/item_progress_habit.xml`
- Modify: `app/src/main/java/com/example/dailyworktracker/ui/common/BindingAdapters.kt` (a `monthLabel` adapter)

**Interfaces:**
- Consumes: everything Task 4 produces; `DayStatus`; `HeatmapAdapter.completedTint/missedTint/skippedTint`; the `visibleIf` and `items` binding adapters.
- Produces: view ids `R.id.recyclerCalendar`, `R.id.recyclerProgressHabits`, `R.id.progressRing`, `R.id.textProgressPercent`, `R.id.groupProgressMode`, `R.id.buttonModeSummary`, `R.id.buttonModeHabits`, `R.id.rowCalendarWeekdays`.

- [ ] **Step 1: Add the month-label binding adapter**

In `BindingAdapters.kt`, beside the other date adapters:

```kotlin
/**
 * Writes a month as "August 2026", or just "August" when it falls in the current year.
 *
 * Formatted here rather than in the ViewModel for the usual reason: naming a month needs a Locale,
 * which belongs to the view layer.
 */
@BindingAdapter("monthLabel")
fun TextView.setMonthLabel(month: YearMonth?) {
    if (month == null) return
    val name = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    text = if (month.year == LocalDate.now().year) name else "$name ${month.year}"
}
```

Add imports `java.time.YearMonth` and `java.time.format.TextStyle`.

- [ ] **Step 2: Create the calendar cell layout**

`res/layout/item_calendar_day.xml` — plain view binding, mirroring `item_heatmap_cell.xml` but without the month band:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingVertical="3dp">

    <FrameLayout
        android:id="@+id/viewCell"
        android:layout_width="34dp"
        android:layout_height="34dp"
        android:layout_gravity="center">

        <TextView
            android:id="@+id/textDay"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:textAppearance="?attr/textAppearanceLabelSmall"
            tools:text="14" />

    </FrameLayout>

</FrameLayout>
```

- [ ] **Step 3: Create the calendar adapter**

`ui/progress/CalendarAdapter.kt`. It reuses the heatmap's drawables and tints so the two calendars cannot drift apart:

```kotlin
package com.example.dailyworktracker.ui.progress

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.R
import com.example.dailyworktracker.databinding.ItemCalendarDayBinding
import com.example.dailyworktracker.ui.common.DayStatus
import com.example.dailyworktracker.ui.habitdetail.HeatmapAdapter
import com.google.android.material.color.MaterialColors

/**
 * One month as a seven-column grid.
 *
 * Boxes are drawn with the same drawables and the same tints as the detail heatmap, so a filled
 * square means the same thing on both screens. The one addition is [DayStatus.PARTIAL], which only
 * arises here: a single habit's day is done or it is not, but a day can hold several habits.
 */
class CalendarAdapter : ListAdapter<CalendarDay, CalendarAdapter.DayViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): DayViewHolder =
        DayViewHolder(ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(
        holder: DayViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class DayViewHolder(
        private val binding: ItemCalendarDayBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CalendarDay) {
            val context = binding.root.context
            val box: View = binding.viewCell

            // A leading blank keeps its slot so the dates stay under the right weekday.
            binding.root.isInvisible = item.date == null
            binding.textDay.text = item.date?.dayOfMonth?.toString().orEmpty()

            when (item.status) {
                DayStatus.COMPLETED -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_filled)
                    box.backgroundTintList =
                        ColorStateList.valueOf(HeatmapAdapter.completedTint(context))
                }

                // Some of the day was kept: the same filled box, softened, so it reads as progress
                // rather than as either a clean sweep or a failure.
                DayStatus.PARTIAL -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_filled)
                    box.backgroundTintList =
                        ColorStateList.valueOf(
                            MaterialColors.compositeARGBWithAlpha(
                                HeatmapAdapter.completedTint(context),
                                PARTIAL_ALPHA,
                            ),
                        )
                }

                DayStatus.SKIPPED -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_filled)
                    box.backgroundTintList =
                        ColorStateList.valueOf(HeatmapAdapter.skippedTint(context))
                }

                DayStatus.PENDING -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_outlined)
                    box.backgroundTintList =
                        ColorStateList.valueOf(HeatmapAdapter.completedTint(context))
                }

                DayStatus.MISSED, DayStatus.OUT_OF_RANGE -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_outlined)
                    box.backgroundTintList =
                        ColorStateList.valueOf(HeatmapAdapter.missedTint(context))
                }

                DayStatus.NOT_SCHEDULED -> {
                    box.background = null
                    box.backgroundTintList = null
                }
            }

            binding.textDay.setTextColor(
                when (item.status) {
                    DayStatus.COMPLETED -> MaterialColors.getColor(box, ON_PRIMARY)
                    else -> MaterialColors.getColor(box, ON_SURFACE_VARIANT)
                },
            )
        }
    }

    private companion object {
        /** Roughly two fifths: visibly lighter than a full day, still clearly filled. */
        const val PARTIAL_ALPHA = 100

        val ON_PRIMARY = com.google.android.material.R.attr.colorOnPrimary
        val ON_SURFACE_VARIANT = com.google.android.material.R.attr.colorOnSurfaceVariant

        val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<CalendarDay>() {
                override fun areItemsTheSame(
                    oldItem: CalendarDay,
                    newItem: CalendarDay,
                ): Boolean = oldItem.date == newItem.date

                override fun areContentsTheSame(
                    oldItem: CalendarDay,
                    newItem: CalendarDay,
                ): Boolean = oldItem == newItem
            }
    }
}
```

`HeatmapAdapter.completedTint` and `missedTint` are already public in its companion object; if `attrColor` is private, keep it that way and only expose the three tint helpers.

- [ ] **Step 4: Create the per-habit row**

`res/layout/item_progress_habit.xml`, data bound to `ProgressHabitUiModel`, following `item_habit.xml`: a `MaterialCardView` holding a `ConstraintLayout` with `textEmoji` (`android:text="@{item.emoji}"`), `textTitle` (`@{item.title}`), a `LinearProgressIndicator` with `android:progress="@{item.percent}"`, and `textPercent` with `android:text="@{@string/progress_habit_rate(item.percent)}"`.

- [ ] **Step 5: Create the habit adapter**

`ui/progress/ProgressHabitAdapter.kt` — a `ListAdapter<ProgressHabitUiModel, …>` binding `item` and setting a root click listener that calls back with the id, exactly like `HabitListAdapter` but with only one callback:

```kotlin
class ProgressHabitAdapter(
    private val onHabitClicked: (habitId: Long) -> Unit,
) : ListAdapter<ProgressHabitUiModel, ProgressHabitAdapter.HabitViewHolder>(DIFF_CALLBACK)
```

with `bind` doing `this.item = item; root.setOnClickListener { onHabitClicked(item.id) }; executePendingBindings()`, and a `DIFF_CALLBACK` comparing `id` then the whole value.

- [ ] **Step 6: Create the screen layout**

`res/layout/fragment_progress.xml`, data bound with a `state` variable of type `ProgressScreenState` and a `viewModel` variable of type `ProgressViewModel`. Structure, following `fragment_habit_list.xml`:

- `AppBarLayout` + `MaterialToolbar` with `app:title="@string/progress_title"`.
- A month stepper row copying the date bar: a previous button with
  `android:onClick="@{() -> viewModel.onPreviousMonthClicked()}"`, a `TextView` with
  `app:monthLabel="@{state.month}"`, and a next button with
  `android:onClick="@{() -> viewModel.onNextMonthClicked()}"` and
  `android:enabled="@{state.canGoToNextMonth}"`.
- A `MaterialButtonToggleGroup` with `app:singleSelection="true"`, holding two buttons that follow
  the ChartRange toggle exactly — `android:checked="@{state.summary}"` /
  `android:onClick="@{() -> viewModel.onModeSelected(ProgressMode.SUMMARY)}"` and the same pair for
  `HABITS`. **`android:onClick`, never a checked-change listener**, because the checked state is
  bound from the state and a change listener would echo straight back.
- A summary block with `app:visibleIf="@{state.showsCalendar}"`: a `CircularProgressIndicator`
  (`android:id="@+id/progressRing"`, `app:indicatorSize="160dp"`, `app:trackThickness="12dp"`,
  `android:progress="@{state.percent}"`, `android:max="100"`) with `textProgressPercent`
  (`android:text="@{@string/progress_habit_rate(state.percent)}"`) centred over it in a `FrameLayout`,
  a label reading `@string/progress_rate_label`, a `LinearLayout` `rowCalendarWeekdays` filled in
  code, and `recyclerCalendar` with `app:items="@{state.days}"`.
- `recyclerProgressHabits` with `app:visibleIf="@{state.showsHabits}"` and `app:items="@{state.habits}"`.
- Loading, empty and error blocks matching `fragment_habit_list.xml`, driven by `state.loading`,
  `state.empty` (with `app:textRes="@{state.emptyTitleRes}"` and `state.emptyMessageRes`) and
  `state.error`.

Add `<import type="com.example.dailyworktracker.ui.progress.ProgressMode" />` in the `<data>` block.

- [ ] **Step 7: Create the Fragment**

`ui/progress/ProgressFragment.kt`, following `HabitListFragment`: `@AndroidEntryPoint`, `viewBinding` delegate, `by viewModels()`, both adapters as fields, `GridLayoutManager(requireContext(), 7)` on the calendar, a weekday header built the same way `HabitDetailFragment.addWeekdayHeader()` does (but with no leading blank, since this grid has no gutter column), `binding.viewModel = viewModel`, `observeUiState()` assigning `binding.state = it`, both adapters nulled in `onDestroyView`, and a habit click navigating to the detail screen. **No bottom inset handling** — it is a tab root laid out above the bar.

- [ ] **Step 8: Build and check it compiles, then commit**

Run the full build command. Expected: green, including the existing suites.

```bash
git add -A
git commit -m "feat: draw the Progress screen"
```

---

### Task 6: The fifth tab

**Files:**
- Modify: `app/src/main/res/navigation/nav_graph.xml`
- Modify: `app/src/main/res/menu/menu_bottom_nav.xml`
- Modify: `app/src/main/java/com/example/dailyworktracker/MainActivity.kt` (`TAB_DESTINATIONS`)
- Create: `app/src/main/res/drawable/ic_progress.xml`
- Modify: `app/src/androidTest/java/com/example/dailyworktracker/ui/BottomNavTest.kt`

**Interfaces:**
- Consumes: `ProgressFragment` (Task 5); `R.string.nav_progress` (Task 4).
- Produces: destination and menu item id `R.id.progressFragment`.

- [ ] **Step 1: Write the failing test**

Add to `BottomNavTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `... .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon "-Pandroid.testInstrumentationRunnerArguments.class=com.example.dailyworktracker.ui.BottomNavTest"`

Expected: compile failure — `R.id.progressFragment` does not exist.

- [ ] **Step 3: Add the icon**

`res/drawable/ic_progress.xml`, matching the other tab icons — 24dp, viewport 24, one white path, **no `android:tint`** so the bar's own item colours apply:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M5,9.2h3V19H5zM10.6,5h2.8v14h-2.8zM16.2,13H19v6h-2.8z" />
</vector>
```

- [ ] **Step 4: Add the destination**

In `nav_graph.xml`, after `allHabitsFragment`:

```xml
    <fragment
        android:id="@+id/progressFragment"
        android:name="com.example.dailyworktracker.ui.progress.ProgressFragment"
        android:label="@string/progress_title"
        tools:layout="@layout/fragment_progress">

        <action
            android:id="@+id/action_progress_to_habitDetail"
            app:destination="@id/habitDetailFragment" />

    </fragment>
```

- [ ] **Step 5: Add the tab, in mockup order**

In `menu_bottom_nav.xml`, between `action_add_habit` and `settingsFragment`:

```xml
    <item
        android:id="@+id/progressFragment"
        android:icon="@drawable/ic_progress"
        android:title="@string/nav_progress" />
```

In `MainActivity`, add it to `TAB_DESTINATIONS`:

```kotlin
        val TAB_DESTINATIONS =
            setOf(
                R.id.habitListFragment,
                R.id.allHabitsFragment,
                R.id.progressFragment,
                R.id.settingsFragment,
            )
```

- [ ] **Step 6: Run the test and confirm it passes**

Run the filtered command from Step 2. Expected: all four `BottomNavTest` tests pass — including `theAddButtonOpensTheSheetWithoutSelectingATab`, which must still hold with five items in the bar.

- [ ] **Step 7: Verify on the emulator**

`connectedDebugAndroidTest` uninstalls the app, so reinstall and seed first:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Seed sample data from Settings, then check:

- the Progress tab opens and the ring shows a plausible percentage for the month;
- the calendar's dates line up under the right weekday letters, and the 1st is in the right column;
- a day with several habits partly kept is visibly different from one where none were;
- swipe a row on Home to skip it, then return to Progress: that day is not red, and the ring does not drop;
- stepping back a month works and the next-month button is disabled on the current month;
- the Habits toggle lists every active habit with its own percentage, and tapping one opens its detail;
- with five items the bar still fits and `+` still opens the sheet without changing the selected tab;
- the detail heatmap now shows skipped days in the muted tone with the third legend entry.

- [ ] **Step 8: Full suite and commit**

Run the full build command, then:

```bash
git add -A
git commit -m "feat: reach Progress from the bottom bar"
```

---

## Self-Review

**Spec coverage.** Fifth bottom-nav item before Settings → Task 6 steps 4-5. One `StateFlow<ProgressScreenState>` with sealed display state and XML-only flat accessors → Task 4 step 4. Summary ring using the month's completion rate across active habits → Task 3 step 4, surfaced in Task 4 step 5 and drawn in Task 5 step 6. Month calendar → Tasks 3, 4 step 5 (`calendarDays`) and 5 steps 2-3. Habits mode, one row per habit, a way into the detail screen → Task 4 step 5 and Task 5 steps 4-5, 7. Navigable month → Task 4 step 5. Skips excluded from the denominator → Task 3 step 4, tested in step 1. The deferred `SKIPPED` status → Task 1 in full. No spec section is unimplemented.

**Type consistency.** `DayStatus` is created at `ui.common` in Task 1 step 3 and gains `PARTIAL` in Task 3 step 3; every later reference uses that package. `HabitMonth` is defined in Task 3 step 4 with fields `habitId`, `scheduleDaysBitmask`, `createdOn`, `completed`, `skipped`, and is built with exactly those names in Task 4 step 5's `toHabitMonth`. `ProgressSummary.completionRate(habits, month, today)`, `rateFor(habit, month, today)` and `dayStatus(habits, date, today)` keep the same parameter order everywhere they are called. `CalendarDay(date, status)` is created in Task 4 step 3 and consumed in Task 5 step 3. `ProgressHabitUiModel.percent` is defined in Task 4 step 3 and used by the layout in Task 5 step 4. `observeAllCompletionDates` / `observeAllSkipDates` are named identically in Task 2 steps 3-5 and Task 4 step 5. `HeatmapAdapter.skippedTint` is added in Task 1 step 5 and used in Task 5 step 3.

**Ordering.** Task 1 must precede Tasks 3 and 5, which need `DayStatus` in `ui.common`. Task 2 must precede Task 4. Task 3 must precede Task 4. Task 5 must precede Task 6, which routes to `ProgressFragment`. No task references anything a later task creates.

**Tripwire.** `StreakCalculator.kt` and `HabitStatistics.kt` appear in no task's file list. Task 3 step 7 verifies this with a `git diff --stat` that must print nothing.
