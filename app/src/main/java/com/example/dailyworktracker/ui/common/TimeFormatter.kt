package com.example.dailyworktracker.ui.common

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats a reminder time the way the device does, e.g. "08:00" or "8:00 AM".
 *
 * Needs a [Context] because whether to use a 12- or 24-hour clock is a per-device *setting*, not a
 * property of the locale: a user in the US can switch their phone to 24-hour time and expects the
 * app to follow. The pattern itself still comes from the locale, so the separator and the position
 * of AM/PM stay correct in every language.
 */
object TimeFormatter {
    fun format(
        context: Context,
        time: LocalTime,
    ): String {
        val locale = Locale.getDefault()
        val skeleton = if (DateFormat.is24HourFormat(context)) SKELETON_24_HOUR else SKELETON_12_HOUR
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        return time.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    private const val SKELETON_24_HOUR = "Hm"
    private const val SKELETON_12_HOUR = "hm"
}
