package com.example.dailyworktracker.util

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the current date.
 *
 * Indirecting `LocalDate.now()` behind an interface keeps "what day is it" injectable, so tests can
 * pin today to a fixed date instead of depending on the clock of the machine running them.
 */
interface DateProvider {
    fun today(): LocalDate
}

@Singleton
class SystemDateProvider @Inject constructor() : DateProvider {
    override fun today(): LocalDate = LocalDate.now()
}
