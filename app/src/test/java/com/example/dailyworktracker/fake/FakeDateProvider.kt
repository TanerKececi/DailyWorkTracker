package com.example.dailyworktracker.fake

import com.example.dailyworktracker.util.DateProvider
import java.time.LocalDate

/**
 * A clock tests control.
 *
 * Defaults to a Monday so weekday-sensitive tests start from a known day, and [advanceTo] lets a
 * test simulate the calendar turning over without touching the machine clock.
 */
class FakeDateProvider(
    private var today: LocalDate = MONDAY,
) : DateProvider {
    override fun today(): LocalDate = today

    fun advanceTo(date: LocalDate) {
        today = date
    }

    companion object {
        val MONDAY: LocalDate = LocalDate.of(2026, 8, 31)
    }
}
