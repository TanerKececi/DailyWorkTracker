package com.example.dailyworktracker.ui.addedithabit

import androidx.annotation.StringRes
import java.time.LocalTime

/**
 * State of the add/edit sheet.
 *
 * Validation failures carry string *resources* rather than resolved text, so the ViewModel stays
 * free of a Context and the view layer decides how to present them.
 */
data class AddEditHabitUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val emoji: String = DEFAULT_EMOJI,
    val scheduleDaysBitmask: Int = 0,
    /**
     * Whether a reminder is switched on, kept apart from [reminderTime] rather than encoded as a
     * null time. The switch and the picker are two controls, and folding them into one field would
     * make switching the reminder off destroy the time the user had chosen.
     */
    val isReminderEnabled: Boolean = false,
    val reminderTime: LocalTime = DEFAULT_REMINDER_TIME,
    val isSaving: Boolean = false,
    @get:StringRes val titleError: Int? = null,
    @get:StringRes val scheduleError: Int? = null,
    /** Set once the habit is persisted, so the sheet knows to dismiss itself. */
    val isSaved: Boolean = false,
) {
    companion object {
        const val DEFAULT_EMOJI = "✅"

        /** Where the time picker starts until the user says otherwise. */
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
    }
}
