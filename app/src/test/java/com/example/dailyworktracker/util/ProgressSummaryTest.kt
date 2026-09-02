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
        // A daily habit kept on none of its days, and a Monday-only habit kept on all five of its
        // Mondays. Weighted that is 5 of 35; averaging the two rates would report a flattering 50%
        // and let the weekly habit hide the daily one.
        val mondays =
            (1..31).map { LocalDate.of(2026, 8, it) }
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

        // 30 resolved daily days (the 31st is today and unfinished) plus 5 Mondays.
        assertEquals(5f / 35f, rate, 0.001f)
    }

    @Test
    fun `days after today do not count against the rate`() {
        // Judged mid-month, the rest of the month has not happened yet.
        val midMonth = LocalDate.of(2026, 8, 10)
        val kept = (1..10).map { LocalDate.of(2026, 8, it) }.toSet()

        val rate =
            ProgressSummary.completionRate(
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

        val rate =
            ProgressSummary.completionRate(
                listOf(habitMonth(createdOn = createdOn, completed = kept)),
                august,
                today = monday,
            )

        assertEquals(1f, rate, 0.001f)
    }

    @Test
    fun `a habit created after the month is left out entirely`() {
        // Guards the empty range: from would otherwise be after to and the loop would misbehave.
        val rate =
            ProgressSummary.completionRate(
                listOf(habitMonth(createdOn = LocalDate.of(2026, 9, 15))),
                august,
                today = monday,
            )

        assertEquals(0f, rate, 0f)
    }

    @Test
    fun `a skipped day leaves the denominator here too`() {
        val kept = (1..30).map { LocalDate.of(2026, 8, it) }.toSet()

        val rate =
            ProgressSummary.completionRate(
                listOf(habitMonth(completed = kept, skipped = setOf(monday))),
                august,
                today = monday,
            )

        assertEquals(1f, rate, 0.001f)
    }

    @Test
    fun `a day everything due was done reads as completed`() {
        val date = LocalDate.of(2026, 8, 10)
        val habits =
            listOf(
                habitMonth(id = 1L, completed = setOf(date)),
                habitMonth(id = 2L, completed = setOf(date)),
            )

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
    fun `a day off the schedule is not reported as skipped`() {
        // Skipped means the habit was due and passed on. A weekday it never repeats on is neither.
        val tuesday = LocalDate.of(2026, 8, 11)
        val mondayOnly = WeekdaySchedule.toBitmask(listOf(DayOfWeek.MONDAY))

        assertEquals(
            DayStatus.NOT_SCHEDULED,
            ProgressSummary.dayStatus(
                listOf(habitMonth(schedule = mondayOnly, skipped = setOf(tuesday))),
                tuesday,
                today = monday,
            ),
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

    @Test
    fun `a day reports the share of what was due that was done`() {
        // The calendar draws each day as a ring, so it needs the proportion, not just a state.
        val date = LocalDate.of(2026, 8, 10)
        val habits =
            listOf(
                habitMonth(id = 1L, completed = setOf(date)),
                habitMonth(id = 2L, completed = setOf(date)),
                habitMonth(id = 3L),
                habitMonth(id = 4L),
            )

        assertEquals(0.5f, ProgressSummary.dayFraction(habits, date), 0.001f)
    }

    @Test
    fun `a day nothing was owed on reports no progress rather than full`() {
        // Zero of zero is not 100%: an empty ring is the honest drawing for a day off.
        val tuesday = LocalDate.of(2026, 8, 11)
        val mondayOnly = WeekdaySchedule.toBitmask(listOf(DayOfWeek.MONDAY))

        assertEquals(0f, ProgressSummary.dayFraction(listOf(habitMonth(schedule = mondayOnly)), tuesday), 0f)
    }

    @Test
    fun `a skipped habit is left out of the day's share, not counted as unfinished`() {
        // One habit due and kept, one skipped: a full ring, because nothing was left owing.
        val date = LocalDate.of(2026, 8, 10)
        val habits =
            listOf(
                habitMonth(id = 1L, completed = setOf(date)),
                habitMonth(id = 2L, skipped = setOf(date)),
            )

        assertEquals(1f, ProgressSummary.dayFraction(habits, date), 0.001f)
    }
}
