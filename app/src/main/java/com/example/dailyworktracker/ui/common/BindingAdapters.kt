package com.example.dailyworktracker.ui.common

import android.view.View
import androidx.databinding.BindingAdapter

/*
 * Binding adapters shared by every data-bound layout.
 *
 * These exist so layouts can express *what* to show while the formatting stays in one place. Any
 * adapter needing a Context takes it from the view it binds, which is why formatters like
 * ScheduleFormatter can stay in the view layer without a ViewModel ever holding a Context.
 */

/**
 * Shows or hides a view, collapsing it rather than leaving a hole.
 *
 * `GONE` rather than `INVISIBLE` because every current use is a whole state group - a spinner, an
 * empty message, an error block - where reserving the space would misalign what is on screen.
 */
@BindingAdapter("visibleIf")
fun View.setVisibleIf(isVisible: Boolean) {
    visibility = if (isVisible) View.VISIBLE else View.GONE
}
