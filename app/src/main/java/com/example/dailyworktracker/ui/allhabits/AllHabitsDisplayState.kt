package com.example.dailyworktracker.ui.allhabits

/**
 * What the manage-habits screen is currently showing.
 *
 * Mutually exclusive states are separate types rather than a set of booleans, so a state that
 * contradicts itself - loading *and* failed, content *and* empty - cannot be constructed.
 *
 * There is no wrapper data class here because nothing on this screen renders across states: the
 * toolbar is static, and every other view belongs to exactly one state. Add a wrapper the day a
 * field has to survive all of them.
 */
sealed interface AllHabitsDisplayState {
    data object Loading : AllHabitsDisplayState

    data class Content(override val habits: List<AllHabitItemUiModel>) : AllHabitsDisplayState

    data object Empty : AllHabitsDisplayState

    data class Error(val throwable: Throwable) : AllHabitsDisplayState

    /*
     * Flat accessors below exist for XML only. Binding expressions have no `when` and no
     * smart-casting, so without these the layout would need Java-style casts. Kotlin callers -
     * ViewModel, tests - match on the types directly instead.
     */

    val isLoading: Boolean get() = this is Loading

    val isContent: Boolean get() = this is Content

    val isEmpty: Boolean get() = this is Empty

    val isError: Boolean get() = this is Error

    /** Empty in every state but [Content], so the list can bind without the layout branching. */
    val habits: List<AllHabitItemUiModel> get() = emptyList()

    val errorMessage: String? get() = (this as? Error)?.throwable?.localizedMessage
}
