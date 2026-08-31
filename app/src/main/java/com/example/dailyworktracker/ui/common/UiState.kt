package com.example.dailyworktracker.ui.common

import androidx.annotation.StringRes

/**
 * What a screen is currently showing.
 *
 * Modelling this as a sealed hierarchy lets Fragments handle every case in one exhaustive `when`,
 * instead of juggling separate `isLoading` / `error` / `items` fields that can contradict each other.
 */
sealed interface UiState<out T> {

    data object Loading : UiState<Nothing>

    data class Success<out T>(val data: T) : UiState<T>

    /**
     * Loaded successfully, but there is nothing to show.
     *
     * The copy travels as string *resources* rather than resolved text, so a ViewModel can explain
     * *why* a screen is empty without holding a Context.
     */
    data class Empty(
        @get:StringRes val titleRes: Int,
        @get:StringRes val messageRes: Int,
    ) : UiState<Nothing>

    data class Error(val throwable: Throwable) : UiState<Nothing>
}
