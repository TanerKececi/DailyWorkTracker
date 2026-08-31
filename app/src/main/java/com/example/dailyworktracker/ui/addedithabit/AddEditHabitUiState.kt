package com.example.dailyworktracker.ui.addedithabit

import androidx.annotation.StringRes

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
    val isSaving: Boolean = false,
    @get:StringRes val titleError: Int? = null,
    @get:StringRes val scheduleError: Int? = null,
    /** Set once the habit is persisted, so the sheet knows to dismiss itself. */
    val isSaved: Boolean = false,
) {
    companion object {
        const val DEFAULT_EMOJI = "✅"
    }
}
