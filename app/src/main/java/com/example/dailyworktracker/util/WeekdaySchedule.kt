package com.example.dailyworktracker.util

import java.time.DayOfWeek

/**
 * Encodes which weekdays a habit is scheduled on as a 7-bit mask.
 *
 * Bit 0 is Monday through bit 6 for Sunday, matching [DayOfWeek.getValue] minus one. Keeping this
 * logic in one pure, dependency-free object makes it trivial to unit test and keeps the SQL simple.
 */
object WeekdaySchedule {

    /** Every day of the week — the mask used for habits like "brush teeth". */
    const val EVERY_DAY: Int = 0b111_1111

    /** No days selected; not a valid habit schedule, used as the "nothing picked yet" state. */
    const val NONE: Int = 0

    fun isScheduledOn(bitmask: Int, day: DayOfWeek): Boolean = bitmask and day.bit() != 0

    fun isEveryDay(bitmask: Int): Boolean = bitmask == EVERY_DAY

    fun hasAnyDay(bitmask: Int): Boolean = bitmask != NONE

    fun toBitmask(days: Iterable<DayOfWeek>): Int = days.fold(NONE) { mask, day -> mask or day.bit() }

    fun toDays(bitmask: Int): List<DayOfWeek> = DayOfWeek.entries.filter { isScheduledOn(bitmask, it) }

    fun withDay(bitmask: Int, day: DayOfWeek, scheduled: Boolean): Int =
        if (scheduled) bitmask or day.bit() else bitmask and day.bit().inv() and EVERY_DAY

    private fun DayOfWeek.bit(): Int = 1 shl (value - 1)
}
