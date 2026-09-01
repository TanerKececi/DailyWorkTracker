package com.example.dailyworktracker.ui.habitdetail

/**
 * What the habit detail screen is currently showing.
 *
 * No wrapper data class: the toolbar's own title is the only thing drawn outside the loaded habit,
 * and it is static. Everything else belongs to exactly one state.
 */
sealed interface HabitDetailDisplayState {
    data object Loading : HabitDetailDisplayState

    data class Content(override val habit: HabitDetailUiState) : HabitDetailDisplayState

    /** Archiving or deleting the habit elsewhere leaves this screen with nothing to show. */
    data object Missing : HabitDetailDisplayState

    data class Error(val throwable: Throwable) : HabitDetailDisplayState

    /*
     * Flat accessors below exist for XML only. Binding expressions have no `when` and no
     * smart-casting, so without these the layout would need Java-style casts. Kotlin callers -
     * ViewModel, tests - match on the types directly instead.
     *
     * Reading through a null [habit] is safe: data binding generates null checks and falls back to
     * default values, and the whole content view is hidden in every state but [Content] anyway.
     */

    val isLoading: Boolean get() = this is Loading

    val isContent: Boolean get() = this is Content

    val isMissing: Boolean get() = this is Missing

    val isError: Boolean get() = this is Error

    val habit: HabitDetailUiState? get() = null

    val errorMessage: String? get() = (this as? Error)?.throwable?.localizedMessage
}
