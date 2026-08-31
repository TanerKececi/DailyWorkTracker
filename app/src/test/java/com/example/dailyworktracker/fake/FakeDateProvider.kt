package com.example.dailyworktracker.fake

import com.example.dailyworktracker.util.DateProvider
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A clock tests control.
 *
 * Defaults to a Monday so weekday-sensitive tests start from a known day, and [advanceTo] lets a
 * test simulate the calendar turning over without touching the machine clock.
 */
class FakeDateProvider(
    private var now: LocalDateTime = MONDAY.atStartOfDay(),
) : DateProvider {
    constructor(today: LocalDate) : this(today.atStartOfDay())

    override fun now(): LocalDateTime = now

    fun advanceTo(date: LocalDate) {
        now = date.atStartOfDay()
    }

    fun advanceTo(dateTime: LocalDateTime) {
        now = dateTime
    }

    companion object {
        val MONDAY: LocalDate = LocalDate.of(2026, 8, 31)
    }
}
