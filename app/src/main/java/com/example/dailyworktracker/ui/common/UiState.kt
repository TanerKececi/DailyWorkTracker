package com.example.dailyworktracker.ui.common

/**
 * What a screen is currently showing.
 *
 * Modelling this as a sealed hierarchy lets Fragments handle every case in one exhaustive `when`,
 * instead of juggling separate `isLoading` / `error` / `items` fields that can contradict each other.
 */
sealed interface UiState<out T> {

    data object Loading : UiState<Nothing>

    data class Success<out T>(val data: T) : UiState<T>

    /** Loaded successfully, but there is nothing to show. */
    data object Empty : UiState<Nothing>

    data class Error(val throwable: Throwable) : UiState<Nothing>
}
