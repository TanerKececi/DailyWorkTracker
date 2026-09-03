# DailyWorkTracker

An Android app for tracking recurring daily habits — brushing teeth, sport, dishes, cleaning the
house — built as a clean-architecture reference: Kotlin, MVVM, XML views with data binding, Room,
and Hilt.

## Features

Four places, reached from a bottom bar — **Home**, **My habits**, **Progress**, **Settings** — with
the add button raised in the middle of it. Adding is the one action in a row of places, so it opens
a sheet rather than becoming a tab of its own.

- **Home** — the habits scheduled for the selected day, checked off with one tap, grouped into *In
  progress*, *Done* and *Skipped*, each showing its streak as it stood that day.
- **Any past day** — a strip of days scrolls along the top, back as far as the oldest habit, with
  the toolbar's date picker as a shortcut for a day much further back. A day you forgot to log can
  be filled in; future days cannot be selected or completed.
- **Skipping a day** — swipe a row left to mark that day as deliberately taken off, and swipe again
  to take the mark back. A skip is neutral: it does not break the streak, and it leaves the
  completion rate alone rather than counting as a miss.
- **Goals** — a habit is either ticked off or logged as a number: times, pages or minutes. The
  number records what was done rather than setting a target, so any amount at all completes the day.
- **Part of the day** — an optional morning, afternoon or evening, which the list can filter by.
- **Progress** — a month at a time across every habit: one completion rate, a calendar drawing each
  day as a ring for how much of it was kept, and a per-habit breakdown behind a toggle.
- **My habits** — every habit regardless of schedule, including archived ones, where they can be
  edited, archived, or restored.
- **History** — per habit: current and best streak, completion rate, and a grid of recent weeks
  showing which days were kept, missed, skipped, or not due.
- **Reminders** — an optional time per habit, delivered as a notification on the days that habit
  repeats, and only when it has not already been ticked off.
- **Home screen widget** — the same list, glanceable, with a tap on a row to tick it off.
- **Add / edit** — name, emoji, a per-weekday schedule with an "every day" shortcut, the goal, the
  part of the day, and the reminder.
- Streaks count consecutive **scheduled** days, so a Mon/Wed/Fri habit is not broken by an untouched
  Tuesday.
- A habit never appears on days before it was created, so browsing back does not invent missed days.
- Archiving is a soft delete: the habit leaves the list but its completion history survives.

Everything is stored locally in Room. There is no account, no network, and no analytics.

## Architecture

```
ui/          Fragments, ViewModels, adapters — one package per screen
  habitlist/ Home: the day strip, the grouped list, the skip swipe
  progress/  the month: ring, calendar, per-habit breakdown
  allhabits/ habitdetail/ addedithabit/ settings/
  common/    binding adapters, ViewBinding delegate, schedule and time formatting
data/
  local/     Room entities, DAOs, database, migrations
  model/     what the repository hands upward: goals, units, part of the day
  repository/HabitRepository (interface) + impl
  sample/    the debug seeder
reminder/    scheduling reminders and posting them
widget/      the home screen widget: provider, row factory, tap handling
di/          Hilt modules
util/        pure logic: weekday bitmask, streaks, statistics, month-wide progress,
             next reminder occurrence, date provider
```

Data flows one way: **Room → Repository → ViewModel → Fragment**, with Kotlin `Flow` throughout.

A few decisions worth knowing:

- **No use-case layer.** There is one aggregate (a habit and its completions) and no cross-repository
  orchestration, so `ViewModel → Repository` is the right amount of layering. Non-trivial rules live
  as pure, injectable classes in `util/` and could be promoted to a `domain/` package if the app
  grows.
- **Completions are their own table**, not a boolean on the habit. That is what makes history and
  streaks possible.
- **Skips are their own table too**, not a flag on a completion, because *a completion row existing
  is exactly what "done" means*. A day is done, skipped, or unresolved, and never two at once — a
  rule enforced at the repository, which clears one when it writes the other.
- **An amount is a record, not a target.** A habit logged in pages is complete at one page, and the
  streak and statistics code therefore takes a plain `Set<LocalDate>`: it only ever asks whether the
  day has a record, never how big it was.
- **Progress composes rather than re-derives.** `ProgressSummary` calls the per-habit
  `HabitStatistics` and sums the results, so the month-wide number and a single habit's own can
  never disagree about what a skipped or unresolved day means. It aggregates **weighted by days
  due** — numerator and denominator summed across habits and divided once — because averaging
  per-habit rates would let a weekly habit that was kept hide a daily one missed all month.
- **One `StateFlow<XxxScreenState>` per screen.** Shared fields sit on a data class and mutually
  exclusive situations in a sealed display state, so "no habits at all" is its own type rather than
  a 0% rate. The flat `isLoading`-style accessors on those types exist for layouts only: binding
  expressions have no `when` and no smart-casting.
- **Data binding without `databinding-ktx`.** The KTX artifact is built against a newer Kotlin than
  this project uses, so there is no StateFlow in XML and no two-way binding: fragments collect and
  assign `binding.state`, and a bound widget reacts through `android:onClick` rather than a
  checked-change listener, which would only echo the bound state straight back.
- **Dates are stored as epoch days.** The repository owns the `LocalDate` conversion, so the rest of
  the app never sees the encoding. `java.time` on `minSdk 24` works via core library desugaring.
- **The repository never reads the clock.** Every date arrives as a parameter, which makes it
  deterministic and leaves exactly one place — the ViewModel's selected date — deciding which day
  the app is showing and writing to.
- **ViewModels never hold a `Context`.** Anything needing one — localized weekday names, validation
  messages, the device's 12- or 24-hour preference — travels as a string resource id or is resolved
  in the view layer.
- **Schedule filtering happens in Kotlin, not SQL**, because a bitmask is far easier to test than a
  bitwise `WHERE` clause.
- **Reminders are a chain of one-shot jobs**, not a periodic one. A `PeriodicWorkRequest` cannot
  express "only on the days this habit repeats", and its period restarts from when the previous run
  *finished*, so every minute of delay is a minute the reminder slides later. Each run recomputes
  the next occurrence from the wall clock instead, so lateness never accumulates.
- **Whether to notify is decided when the job fires**, not when it was scheduled. By then the habit
  may have been ticked off, archived, moved onto other weekdays or deleted, so a stale pending job
  costs a wasted wake-up rather than a wrong notification.
- **Reminder scheduling and widget refreshes hang off the repository's write path**, not off
  the screens that trigger the writes. The repository is the one place a habit or a
  completion can change, so it is the only place where what is stored and what the outside
  world shows cannot drift apart — archiving alone is reachable from two screens.

## Database migrations

The database is at **version 4**, and `DatabaseModule` builds it with a plain `.build()` and
**deliberately no destructive fallback**: losing someone's habit history on upgrade is never the
right answer. The cost is that a version bump without a matching migration throws the first time the
app opens, for every existing install — so a schema change is not done until its migration exists.

Schemas are exported to `app/schemas/` (1–4), and the migration SQL is written by copying what Room
exported rather than by hand, or the identity hash will not validate. `MigrationTest` covers the
individual steps and the whole 1→4 chain; add a chained case for any new version.

## Requirements

- Android Studio (recent stable) with an emulator or device on **API 24+**
- **JDK 21** — Android Studio's bundled JBR works. Newer JDKs may not: Gradle 8.13 does not support
  JDK 26.

## Building

From Android Studio, open the project and press Run.

From the command line:

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

### Windows note

If Gradle fails with `Unable to establish loopback connection`, its daemon cannot create the
AF_UNIX socket it uses for IPC — typically because security software blocks socket files under the
user's `Temp` directory, or because the default JDK is too new. Point both away from the defaults:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:TEMP = "C:\gradle-tmp"
$env:TMP  = "C:\gradle-tmp"
.\gradlew.bat :app:assembleDebug
```

These are deliberately kept out of `gradle.properties`: the paths are machine-specific and would
break other machines and CI.

## Testing

```bash
./gradlew :app:testDebugUnitTest        # unit tests, JVM only
./gradlew :app:connectedDebugAndroidTest # instrumentation, needs a device
./gradlew :app:ktlintCheck               # formatting gate (ktlintFormat to fix)
```

Unit tests run against `FakeHabitRepository`, a working in-memory fake that applies the same
schedule filtering and toggle semantics as the real implementation, so tests exercise realistic
behaviour without Room or Android. `DateProvider` lets tests pin "now" instead of depending on the
clock, and `ReminderSchedule` is pure, so every awkward reminder case — the time has already passed
today, today is not a scheduled day, only one weekday repeats — is an ordinary unit test rather than
something you can only check by waiting.

The instrumentation suite covers what JVM tests structurally cannot reach. One is a view-lifecycle
regression: the ViewBinding delegate once handed a stale binding to a freshly created view, so
navigating away and back left the screen unwired — `HabitListNavigationTest` asserts the list keeps
its adapter across a round trip. Another is app startup — reminders require WorkManager to be
initialised by the `Application` (see below), and under test the `Application` is Hilt's, so
`HiltTestRunner` has to stand WorkManager up itself. Without that, every screen that reaches the
scheduler dies on injection, and only an on-device test notices. The rest is real SQLite:
`MigrationTest` upgrades an actual database file, which is the only way to find out whether a
migration validates.

Note that `connectedDebugAndroidTest` uninstalls the app afterwards, which wipes the database. Expect
to re-seed sample data (below) after running it.

### Verifying the widget

Long press the home screen, choose **Widgets**, and drag the DailyWorkTracker widget out.
The day-rollover path is worth exercising by hand at least once: in Settings, turn
**Automatic date and time** off and move the date forward a day. The widget should redraw on
its own, drop habits that do not repeat on the new weekday, and recompute the streaks.

### Verifying reminders without waiting

A reminder scheduled for tomorrow morning is awkward to test by hand. The pending jobs and their
delays are visible in `dumpsys`, and a job can be forced to run immediately:

```bash
adb shell dumpsys jobscheduler | grep -B8 "Trace tag: HabitReminderWorker"
adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler com.example.dailyworktracker 0
```

## Reminders

`HabitReminderScheduler` is an interface so the repository, which calls it on every write, stays a
plain class that unit tests can build without WorkManager or a Context. The WorkManager
implementation keys work by a unique name per habit, which is what stops an edit from leaving two
reminders running for the same one.

Because the worker takes constructor dependencies, it has to be built by Hilt's worker factory
rather than WorkManager's default. That means the manifest removes WorkManager's own startup
initializer and `DailyWorkTrackerApp` supplies the `Configuration` instead.

`POST_NOTIFICATIONS` is requested the moment a reminder is switched on rather than at app start, so
the request arrives with an obvious reason attached and someone who never sets a reminder is never
asked. If it is refused, the editor says the reminder will not be delivered instead of silently
saving one that never arrives.

## Home screen widget

Built on `RemoteViews` rather than Glance. Glance would pull the Compose compiler and
runtime into a project that is otherwise XML views throughout, for one surface. The cost is
that RemoteViews supports only a fixed set of views — no ConstraintLayout, no
MaterialCardView, and no CheckBox before API 31 — so the widget has its own layouts and an
ImageView standing in for the checkbox.

It also cannot resolve `?attr/colorSurface` and the rest of the Material theme, because the
launcher draws it and not our Activity. The palette is therefore written out as concrete
Material 3 baseline colours, with a `values-night` copy so the widget still follows the
system's dark mode.

Tapping a row ticks that habit off; tapping the header opens the app. It is that way round
because a row inside a RemoteViews collection cannot carry its own `PendingIntent` — every
row fills in a single template the provider sets on the list — so there is exactly one
component any row tap can reach. Letting part of a row open the app instead would mean the
receiver calling `startActivity` from the background, which Android 10 onwards blocks.

That tap goes to `HabitWidgetActionReceiver`, which is deliberately *not* exported, unlike
the provider, which has to be so the AppWidget framework can broadcast to it. A
`PendingIntent` carries the identity of the app that created it, so the launcher can still
fire an unexported receiver and nothing else can tick a habit off.

## Sample data (debug builds)

A single freshly created habit makes the history grid, streaks and completion rate impossible to
judge. In a debug build, the Settings tab has **Insert sample data**, which replaces the database
with a few months of plausible history: a near-perfect daily habit, a Mon/Wed/Fri one, a patchy one,
weekend-only, weekdays-only, one started two weeks ago, and an archived one. Each has its own
adherence rate, which is what gives the grid real texture. Between them they cover both kinds of
goal and all three parts of the day, and two carry reminder times, so neither the amount UI nor a
reminder has to be set up by hand to be seen. Two of them also take deliberate rest days now and
then — sparingly, because a habit peppered with skips would misrepresent what a skip is for.

Because it covers every field a habit has, it needs updating whenever one is added; a seeder that
has fallen behind makes the newest feature the one thing that cannot be demonstrated.

The generator is seeded, so the same data comes back every run and screenshots stay comparable. It
is destructive, and hidden unless `BuildConfig.DEBUG` — see `data/sample/SampleDataSeeder.kt`.

## Known limitations

- **The emoji field accepts any short text**, not strictly an emoji.
- **A skip commits on release, with Undo in the snackbar**, rather than swiping to reveal a Skip
  button you then tap. Both are a confirm step, just inverted, and this is the shape Material
  recommends. The other one cannot be built on `ItemTouchHelper`, which has no way to hold a row
  open, so it would mean hand-rolled touch handling.
- **There are no targets** — no "12 of 20 pages". An amount is a record of what was done, and any
  amount completes the day; see the goals decision above. Adding one later would change what a
  completion row means, which is the assumption the streak and statistics code is built on.
- **Reminders are not exact.** WorkManager guarantees a job runs no *earlier* than its time, not
  that it runs on time: under Doze a reminder can arrive minutes, occasionally hours, late. Exact
  delivery needs `AlarmManager.setExactAndAllowWhileIdle` plus the `SCHEDULE_EXACT_ALARM`
  permission from API 31. The trade taken here is that WorkManager persists its own queue, so
  reminders survive a reboot without a `BOOT_COMPLETED` receiver. Lateness at least does not
  accumulate: a reminder delivered late still schedules the next one for the right time.
- **A reminder already queued when the time zone changes fires at the old offset**, because a job's
  delay is measured in elapsed time rather than wall-clock time. Opening the app re-arms every
  reminder and corrects it, and so does that reminder firing once.
- **Travelling west across a time zone** can move the device's date backwards. The selected day is
  clamped to today when the screen resumes, so the app stays consistent, but a day selected before
  the change becomes unreachable until the clock catches up.
- **A reminder cannot be acted on from the notification.** Tapping it opens the app; there is no
  "mark done" action, which would need a receiver and a second write path into completions.
- **The widget always shows today**, and has no per-instance configuration. Its rows tick habits
  off; only the header opens the app, for the RemoteViews reason described above.
- **The widget does not use Android 12 dynamic colour.** Its palette is the Material 3 baseline
  written out as literal colours, because a widget cannot resolve the app's theme attributes, so it
  will not pick up the wallpaper-derived colours the launcher uses around it.
