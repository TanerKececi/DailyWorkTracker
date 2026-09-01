package com.example.dailyworktracker.ui.habitdetail

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.dailyworktracker.R
import com.example.dailyworktracker.databinding.FragmentHabitDetailBinding
import com.example.dailyworktracker.ui.common.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** One habit's history: streaks, completion rate, and a grid of recent days. */
@AndroidEntryPoint
class HabitDetailFragment : Fragment(R.layout.fragment_habit_detail) {
    private val binding by viewBinding(FragmentHabitDetailBinding::bind)
    private val viewModel: HabitDetailViewModel by viewModels()

    private val heatmapAdapter = HeatmapAdapter()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        applyWindowInsets()
        binding.viewModel = viewModel
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        setUpHeatmap()
        observeUiState()
    }

    override fun onDestroyView() {
        binding.recyclerHeatmap.adapter = null
        super.onDestroyView()
    }

    private fun setUpHeatmap() {
        // Seven columns means one row is one week, which is what makes the header line up.
        binding.recyclerHeatmap.layoutManager = GridLayoutManager(requireContext(), HabitDetailViewModel.COLUMNS)
        binding.recyclerHeatmap.adapter = heatmapAdapter
        addWeekdayHeader()
        tintLegend()
    }

    private fun addWeekdayHeader() {
        val header = binding.rowWeekdayHeader
        header.removeAllViews()
        // A blank slot under the month gutter, so the letters sit over their own columns.
        header.addView(headerLabel(""))
        DayOfWeek.entries.forEach { day ->
            header.addView(headerLabel(day.getDisplayName(TextStyle.NARROW, Locale.getDefault())))
        }
    }

    /** Tinted from the adapter so the key always matches the boxes the grid draws. */
    private fun tintLegend() {
        val context = requireContext()
        binding.legendHeatmap.swatchDone.backgroundTintList =
            ColorStateList.valueOf(HeatmapAdapter.completedTint(context))
        binding.legendHeatmap.swatchMissed.backgroundTintList =
            ColorStateList.valueOf(HeatmapAdapter.missedTint(context))
    }

    /** Equal-weight so the header divides into the same columns as the grid below it. */
    private fun headerLabel(text: String): TextView =
        TextView(requireContext()).apply {
            this.text = text
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
        }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bottom =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                ).bottom
            view.updatePadding(bottom = bottom)
            insets
        }
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
}
