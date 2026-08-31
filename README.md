# DailyWorkTracker

An Android app for tracking recurring daily habits — brushing teeth, sport, dishes, cleaning the
house — built as a clean-architecture reference: Kotlin, MVVM, XML views with ViewBinding, Room, and
Hilt.

## Features

- **Today** — the habits scheduled for the selected day, checked off with one tap, each showing its
  streak as it stood that day.
- **Any past day** — step back and forward a day at a time, or jump to any date with the picker, and
  fill in a day you forgot to log. Future days cannot be selected or completed.
- **All habits** — every habit regardless of schedule, including archived ones, where they can be
  edited, archived, or restored.
- **History** — per habit: current and best streak, completion rate, and a grid of recent weeks
  showing which days were kept, missed, or not due.
- **Add / edit** — name, emoji, and a per-weekday schedule with an "every day" shortcut.
- Streaks count consecutive **scheduled** days, so a Mon/Wed/Fri habit is not broken by an untouched
  Tuesday.
- A habit never appears on days before it was created, so browsing back does not invent missed days.
- Archiving is a soft delete: the habit leaves the list but its completion history survives.

Everything is stored locally in Room. There is no account, no network, and no analytics.

## Architecture

```
ui/          Fragments, ViewModels, adapters — one package per screen
  common/    UiState, ViewBinding delegate, schedule formatting
data/
  local/     Room entities, DAOs, database
  model/     what the repository hands upward
  repository/HabitRepository (interface) + impl
di/          Hilt modules
util/        pure logic: weekday bitmask, streaks, date provider
```

Data flows one way: **Room → Repository → ViewModel → Fragment**, with Kotlin `Flow` throughout.

A few decisions worth knowing:

- **No use-case layer.** There is one aggregate (a habit and its completions) and no cross-repository
  orchestration, so `ViewModel → Repository` is the right amount of layering. Non-trivial rules live
  as pure, injectable classes in `util/` and could be promoted to a `domain/` package if the app
  grows.
- **Completions are their own table**, not a boolean on the habit. That is what makes history and
  streaks possible.
- **Dates are stored as epoch days.** The repository owns the `LocalDate` conversion, so the rest of
  the app never sees the encoding. `java.time` on `minSdk 24` works via core library desugaring.
- **The repository never reads the clock.** Every date arrives as a parameter, which makes it
  deterministic and leaves exactly one place — the ViewModel's selected date — deciding which day
  the app is showing and writing to.
- **ViewModels never hold a `Context`.** Anything needing one — localized weekday names, validation
  messages — travels as a string resource id and is resolved in the view layer.
- **Schedule filtering happens in Kotlin, not SQL**, because a bitmask is far easier to test than a
  bitwise `WHERE` clause.

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
behaviour without Room or Android. `DateProvider` lets tests pin "today" instead of depending on the
clock.

The instrumentation suite covers a view-lifecycle regression that JVM tests structurally cannot
reach: the ViewBinding delegate once handed a stale binding to a freshly created view, so navigating
away and back left the screen unwired.

Note that `connectedDebugAndroidTest` uninstalls the app afterwards, which wipes the database. Expect
to re-seed sample data (below) after running it.

## Sample data (debug builds)

A single freshly created habit makes the history grid, streaks and completion rate impossible to
judge. In a debug build, the overflow menu on the today screen has **Insert sample data**, which
replaces the database with a few months of plausible history: a near-perfect daily habit, a
Mon/Wed/Fri one, a patchy one, weekend-only, weekdays-only, one started two weeks ago, and an
archived one. Each has its own adherence rate, which is what gives the grid real texture.

The generator is seeded, so the same data comes back every run and screenshots stay comparable. It
is destructive, and hidden unless `BuildConfig.DEBUG` — see `data/sample/SampleDataSeeder.kt`.

## Known limitations

- **The emoji field accepts any short text**, not strictly an emoji.
- **Travelling west across a time zone** can move the device's date backwards. The selected day is
  clamped to today when the screen resumes, so the app stays consistent, but a day selected before
  the change becomes unreachable until the clock catches up.
- **No reminders yet.** The habit model already carries a nullable reminder time so notifications can
  be added without a migration.
