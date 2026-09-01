package com.example.dailyworktracker.ui.addedithabit

import androidx.annotation.StringRes
import com.example.dailyworktracker.R
import com.example.dailyworktracker.data.model.HabitGoal
import com.example.dailyworktracker.data.model.HabitUnit
import com.example.dailyworktracker.data.model.TimeOfDay
import com.example.dailyworktracker.util.WeekdaySchedule
import java.time.LocalTime

/**
 * State of the add/edit sheet.
 *
 * Every field of the form is shared data: it is on screen whether the sheet is being filled in,
 * saving, or done. Only the three situations the sheet can be *in* are mutually exclusive, and
 * those live in [displayState].
 */
data class AddEditHabitScreenState(
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
    /**
     * Whether the habit is logged as a number, kept apart from [unit] for the same reason
     * [isReminderEnabled] is kept apart from [reminderTime]: switching tracking off must not
     * destroy the unit the user had already picked.
     */
    val isAmountTracked: Boolean = false,
    val unit: HabitUnit = HabitUnit.TIMES,
    /** Which part of the day the habit belongs to; null is "any time". */
    val timeOfDay: TimeOfDay? = null,
    val displayState: AddEditHabitDisplayState = AddEditHabitDisplayState.Editing(),
) {
    val isEveryDay: Boolean get() = WeekdaySchedule.isEveryDay(scheduleDaysBitmask)

    /** What actually gets saved: the two fields above collapsed into the stored shape. */
    val goal: HabitGoal
        get() = if (isAmountTracked) HabitGoal.Amount(unit) else HabitGoal.Once

    @get:StringRes
    val sheetTitleRes: Int
        get() = if (isEditing) R.string.edit_habit_title else R.string.add_habit_title

    /*
     * Flat accessors below exist for XML only. Binding expressions have no `when` and no
     * smart-casting, so without these the layout would need Java-style casts. Kotlin callers -
     * ViewModel, tests - match on `displayState` directly instead.
     */

    val isSaving: Boolean get() = displayState is AddEditHabitDisplayState.Saving

    val isSaved: Boolean get() = displayState is AddEditHabitDisplayState.Saved

    @get:StringRes
    val titleError: Int?
        get() = (displayState as? AddEditHabitDisplayState.Editing)?.titleError

    @get:StringRes
    val scheduleError: Int?
        get() = (displayState as? AddEditHabitDisplayState.Editing)?.scheduleError

    companion object {
        const val DEFAULT_EMOJI = "✅"

        /** Where the time picker starts until the user says otherwise. */
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

/**
 * What the sheet is doing, as opposed to what it holds.
 *
 * Three objects rather than the `isSaving` / `isSaved` / two-error combination they replace: those
 * four fields could represent sixteen situations, of which only three were ever meant to happen.
 */
sealed interface AddEditHabitDisplayState {
    /** Being filled in. Validation failures belong to this state and to no other. */
    data class Editing(
        @get:StringRes val titleError: Int? = null,
        @get:StringRes val scheduleError: Int? = null,
    ) : AddEditHabitDisplayState

    data object Saving : AddEditHabitDisplayState

    /** Persisted, so the sheet knows to dismiss itself. */
    data object Saved : AddEditHabitDisplayState
}
