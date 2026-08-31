package com.example.dailyworktracker.util

import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the current date and time.
 *
 * Indirecting the clock behind an interface keeps "what time is it" injectable, so tests can pin the
 * moment instead of depending on the machine running them. [now] is the single primitive: deriving
 * [today] from it means the two can never disagree about which day it is.
 */
interface DateProvider {
    fun now(): LocalDateTime

    fun today(): LocalDate = now().toLocalDate()
}

@Singleton
class SystemDateProvider
    @Inject
    constructor() : DateProvider {
        override fun now(): LocalDateTime = LocalDateTime.now()
    }
