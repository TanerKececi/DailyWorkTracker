package com.example.dailyworktracker.ui.progress

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.dailyworktracker.R
import com.example.dailyworktracker.databinding.FragmentProgressBinding
import com.example.dailyworktracker.ui.common.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** How every habit is going this month: a rate for all of them together, or one row each. */
@AndroidEntryPoint
class ProgressFragment : Fragment(R.layout.fragment_progress) {
    private val binding by viewBinding(FragmentProgressBinding::bind)
    private val viewModel: ProgressViewModel by viewModels()

    private val calendarAdapter = CalendarAdapter()
    private val habitAdapter =
        ProgressHabitAdapter(
            onHabitClicked = { habitId ->
                findNavController().navigate(
                    ProgressFragmentDirections.actionProgressToHabitDetail(habitId),
                )
            },
        )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        // The month stepper and the toggle are wired in the layout; only the grids need code.
        binding.viewModel = viewModel
        binding.recyclerCalendar.layoutManager = GridLayoutManager(requireContext(), DAYS_PER_WEEK)
        binding.recyclerCalendar.adapter = calendarAdapter
        binding.recyclerProgressHabits.adapter = habitAdapter
        addWeekdayHeader()
        observeUiState()
    }

    override fun onDestroyView() {
        // The adapters outlive the view here, so drop the RecyclerViews' references to them.
        binding.recyclerCalendar.adapter = null
        binding.recyclerProgressHabits.adapter = null
        super.onDestroyView()
    }

    /*
     * No bottom inset handling: the Activity lays this screen out above the bottom bar, and the bar
     * consumes the gesture inset itself.
     */

    /** Seven equal-weight labels, so the header divides into the same columns as the grid below. */
    private fun addWeekdayHeader() {
        val header = binding.rowCalendarWeekdays
        header.removeAllViews()
        DayOfWeek.entries.forEach { day ->
            header.addView(headerLabel(day.getDisplayName(TextStyle.NARROW, Locale.getDefault())))
        }
    }

    private fun headerLabel(text: String): TextView =
        TextView(requireContext()).apply {
            this.text = text
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
        }

    /**
     * The layout renders itself from the state; this only hands each new value over.
     *
     * The state is assigned rather than bound as a StateFlow because databinding-ktx, which would
     * observe the flow directly, is disabled - see the note in the module's build file.
     */
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { binding.state = it }
            }
        }
    }

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}
