package com.example.dailyworktracker.data.sample

import com.example.dailyworktracker.data.local.dao.HabitCompletionDao
import com.example.dailyworktracker.data.local.dao.HabitDao
import com.example.dailyworktracker.data.local.dao.HabitSkipDao
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.local.entity.HabitCompletion
import com.example.dailyworktracker.data.local.entity.HabitSkip
import com.example.dailyworktracker.data.model.HabitGoal
import com.example.dailyworktracker.data.model.HabitUnit
import com.example.dailyworktracker.data.model.TimeOfDay
import com.example.dailyworktracker.data.model.withGoal
import com.example.dailyworktracker.data.model.withTimeOfDay
import com.example.dailyworktracker.reminder.HabitReminderScheduler
import com.example.dailyworktracker.util.DateProvider
import com.example.dailyworktracker.util.WeekdaySchedule
import com.example.dailyworktracker.util.withReminderTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.random.nextInt

/**
 * Fills the database with a plausible few months of history so the UI can be judged with real
 * shapes: partial schedules, broken streaks, a recently started habit, and an archived one.
 *
 * **Debug only.** Callers must guard on `BuildConfig.DEBUG`; it is destructive, replacing whatever
 * is stored. The randomness is seeded, so the same data comes back every run and a screenshot taken
 * today can be compared with one taken later.
 */
@Singleton
class SampleDataSeeder
    @Inject
    constructor(
        private val habitDao: HabitDao,
        private val completionDao: HabitCompletionDao,
        private val skipDao: HabitSkipDao,
        private val dateProvider: DateProvider,
        private val reminderScheduler: HabitReminderScheduler,
    ) {
        suspend fun seed() {
            // Skips and completions both hang off habits with ON DELETE CASCADE, so clearing the
            // habits clears their history too; there is nothing else to wipe.
            habitDao.deleteAll()

            val today = dateProvider.today()
            val random = Random(SEED)

            SAMPLES.forEach { sample ->
                val startedOn = today.minusWeeks(sample.weeksOfHistory)
                val habit =
                    Habit(
                        title = sample.title,
                        emoji = sample.emoji,
                        scheduleDaysBitmask = sample.schedule,
                        createdAt = startedOn.toEpochMilli(),
                        isArchived = sample.isArchived,
                    ).withGoal(sample.goal)
                        .withTimeOfDay(sample.timeOfDay)
                        .withReminderTime(sample.reminderTime)
                val habitId = habitDao.insert(habit)
                // Inserted straight through the DAO, so the reminder has to be scheduled here
                // too; the repository, which normally does it, is bypassed by this tool.
                reminderScheduler.schedule(habit.copy(id = habitId))

                generateSequence(startedOn) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(today) }
                    .filter { WeekdaySchedule.isScheduledOn(sample.schedule, it.dayOfWeek) }
                    // The final day is left open so "today" still has something to tick off.
                    .filter { it != today }
                    .forEach { date ->
                        // Skipped is drawn first and wins outright, because the two are mutually
                        // exclusive everywhere else: the repository clears one when it writes the
                        // other, and seeded data that broke that rule would only ever be a lie.
                        if (random.nextFloat() < sample.skipRate) {
                            skipDao.insert(
                                HabitSkip(
                                    habitId = habitId,
                                    date = date.toEpochDay(),
                                    createdAt = date.toEpochMilli(),
                                ),
                            )
                            return@forEach
                        }
                        if (random.nextFloat() >= sample.adherence) return@forEach

                        completionDao.insert(
                            HabitCompletion(
                                habitId = habitId,
                                date = date.toEpochDay(),
                                completedAt = date.toEpochMilli(),
                                // Drawn from the same seeded Random as adherence, so a re-seed
                                // reproduces the identical history.
                                amount =
                                    sample.unit?.let { random.nextInt(sample.amountRange) },
                            ),
                        )
                    }
            }
        }

        private fun LocalDate.toEpochMilli(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        private data class Sample(
            val title: String,
            val emoji: String,
            val schedule: Int,
            val weeksOfHistory: Long,
            /** Chance a scheduled day was kept, which is what gives each habit its own texture. */
            val adherence: Float,
            /**
             * Chance a scheduled day was deliberately skipped instead.
             *
             * Zero for most samples: a rest day is something a person chooses now and then, so a
             * habit peppered with them would misrepresent the feature. The point is to have enough
             * on screen to see that a skip reads as neutral rather than as a miss.
             */
            val skipRate: Float = 0f,
            /** A couple of samples carry one so reminders are visible without setting one up. */
            val reminderTime: LocalTime? = null,
            val isArchived: Boolean = false,
            /** Null leaves the habit ticked off; a unit makes it logged as a number. */
            val unit: HabitUnit? = null,
            /** What a logged day draws from, so each habit's chart has its own plausible spread. */
            val amountRange: IntRange = 1..1,
            /** Null leaves the habit tied to no particular time, which is the default. */
            val timeOfDay: TimeOfDay? = null,
        ) {
            val goal: HabitGoal get() = unit?.let(HabitGoal::Amount) ?: HabitGoal.Once
        }

        private companion object {
            const val SEED = 42

            val WEEKDAYS =
                WeekdaySchedule.toBitmask(
                    listOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                    ),
                )

            val MON_WED_FRI =
                WeekdaySchedule.toBitmask(
                    listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                )

            val WEEKEND = WeekdaySchedule.toBitmask(listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))

            val SAMPLES =
                listOf(
                    // Near perfect: shows a long streak and a dense grid.
                    Sample(
                        "Brush teeth",
                        "🪥",
                        WeekdaySchedule.EVERY_DAY,
                        14,
                        0.97f,
                        reminderTime = LocalTime.of(7, 30),
                        timeOfDay = TimeOfDay.MORNING,
                    ),
                    // Partial schedule, logged in minutes: an off-day gap in both grid and chart.
                    // Also the clearest case for rest days - a training week usually has one.
                    Sample(
                        "Do sport",
                        "🏃",
                        MON_WED_FRI,
                        12,
                        0.78f,
                        skipRate = 0.12f,
                        reminderTime = LocalTime.of(18, 0),
                        unit = HabitUnit.MINUTES,
                        amountRange = 20..60,
                        timeOfDay = TimeOfDay.AFTERNOON,
                    ),
                    // Patchy: the interesting case for streaks and completion rate.
                    Sample("Wash dishes", "🍽", WeekdaySchedule.EVERY_DAY, 10, 0.55f),
                    // Skipped often enough to show a streak surviving one: a weekend away.
                    Sample("Clean the house", "🧹", WEEKEND, 12, 0.7f, skipRate = 0.15f),
                    // Logged in pages: a wide spread, so the chart's bars differ noticeably.
                    Sample(
                        "Read",
                        "📚",
                        WEEKDAYS,
                        9,
                        0.88f,
                        unit = HabitUnit.PAGES,
                        amountRange = 5..40,
                        timeOfDay = TimeOfDay.EVENING,
                    ),
                    // Started recently: the grid should show one short block, not months of blanks.
                    // Also the small-numbers case, where every bar is a handful.
                    Sample(
                        "Drink water",
                        "💧",
                        WeekdaySchedule.EVERY_DAY,
                        2,
                        0.9f,
                        unit = HabitUnit.TIMES,
                        amountRange = 4..10,
                    ),
                    // Archived: only reachable from All habits.
                    Sample("Meditate", "🧘", WeekdaySchedule.EVERY_DAY, 8, 0.6f, isArchived = true),
                )
        }
    }
