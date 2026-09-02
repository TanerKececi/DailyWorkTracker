package com.example.dailyworktracker.ui.common

/**
 * How a single day should be drawn in a calendar grid.
 *
 * Shared by the detail heatmap, which shows one habit, and the Progress calendar, which shows every
 * habit at once - so a filled square means the same thing on both screens.
 */
enum class DayStatus {
    /** Due that day and done: a ticked box. */
    COMPLETED,

    /**
     * Some but not all of the habits due that day were done.
     *
     * Only the Progress calendar produces this: one habit's day is done or it is not. Without it a
     * day four habits out of five were kept on would look like a day nothing happened.
     */
    PARTIAL,

    /** Due that day and missed: an empty box. Only assigned to days that have already resolved. */
    MISSED,

    /**
     * Deliberately skipped.
     *
     * Neutral, like a day the habit was never due: it does not break a streak and it leaves the
     * completion-rate denominator. Drawn distinctly from [MISSED] so the grid agrees with the
     * numbers above it.
     */
    SKIPPED,

    /** The habit does not repeat on that weekday, so no box is drawn at all. */
    NOT_SCHEDULED,

    /** Due today and not done yet. Distinct from [MISSED]: the day has not resolved. */
    PENDING,

    /** Before the habit existed, or later than today. Drawn as an empty slot. */
    OUT_OF_RANGE,
}
