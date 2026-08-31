package com.example.dailyworktracker.data.sample

import com.example.dailyworktracker.data.local.dao.HabitCompletionDao
import com.example.dailyworktracker.data.local.dao.HabitDao
import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.local.entity.HabitCompletion
import com.example.dailyworktracker.util.DateProvider
import com.example.dailyworktracker.util.WeekdaySchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

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
        private val dateProvider: DateProvider,
    ) {
        suspend fun seed() {
            habitDao.deleteAll()

            val today = dateProvider.today()
            val random = Random(SEED)

            SAMPLES.forEach { sample ->
                val startedOn = today.minusWeeks(sample.weeksOfHistory)
                val habitId =
                    habitDao.insert(
                        Habit(
                            title = sample.title,
                            emoji = sample.emoji,
                            scheduleDaysBitmask = sample.schedule,
                            createdAt = startedOn.toEpochMilli(),
                            isArchived = sample.isArchived,
                        ),
                    )

                generateSequence(startedOn) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(today) }
                    .filter { WeekdaySchedule.isScheduledOn(sample.schedule, it.dayOfWeek) }
                    // The final day is left open so "today" still has something to tick off.
                    .filter { it != today }
                    .filter { random.nextFloat() < sample.adherence }
                    .forEach { date ->
                        completionDao.insert(
                            HabitCompletion(
                                habitId = habitId,
                                date = date.toEpochDay(),
                                completedAt = date.toEpochMilli(),
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
            val isArchived: Boolean = false,
        )

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
                    Sample("Brush teeth", "🪥", WeekdaySchedule.EVERY_DAY, 14, 0.97f),
                    // Partial schedule: proves the grid leaves off-days blank.
                    Sample("Do sport", "🏃", MON_WED_FRI, 12, 0.78f),
                    // Patchy: the interesting case for streaks and completion rate.
                    Sample("Wash dishes", "🍽", WeekdaySchedule.EVERY_DAY, 10, 0.55f),
                    Sample("Clean the house", "🧹", WEEKEND, 12, 0.7f),
                    Sample("Read", "📚", WEEKDAYS, 9, 0.88f),
                    // Started recently: the grid should show one short block, not months of blanks.
                    Sample("Drink water", "💧", WeekdaySchedule.EVERY_DAY, 2, 0.9f),
                    // Archived: only reachable from All habits.
                    Sample("Meditate", "🧘", WeekdaySchedule.EVERY_DAY, 8, 0.6f, isArchived = true),
                )
        }
    }
