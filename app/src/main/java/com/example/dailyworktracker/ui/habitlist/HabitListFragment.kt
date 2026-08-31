package com.example.dailyworktracker.ui.habitlist

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.dailyworktracker.R
import com.example.dailyworktracker.databinding.FragmentHabitListBinding
import com.example.dailyworktracker.ui.addedithabit.AddEditHabitViewModel.Companion.NEW_HABIT_ID
import com.example.dailyworktracker.ui.common.DateLabelFormatter
import com.example.dailyworktracker.ui.common.UiState
import com.example.dailyworktracker.ui.common.viewBinding
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Shows the habits due on the selected day and lets the user tick them off, today or in the past. */
@AndroidEntryPoint
class HabitListFragment : Fragment(R.layout.fragment_habit_list) {
    private val binding by viewBinding(FragmentHabitListBinding::bind)
    private val viewModel: HabitListViewModel by viewModels()

    private val habitAdapter =
        HabitListAdapter(
            onToggleCompleted = { habitId -> viewModel.onHabitCheckedChanged(habitId) },
            onMoreClicked = { item, anchor -> showHabitMenu(item, anchor) },
        )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        applyWindowInsets()
        setUpToolbarMenu()
        setUpDateBar()
        binding.recyclerHabits.adapter = habitAdapter
        binding.fabAddHabit.setOnClickListener { navigateToHabitEditor(NEW_HABIT_ID) }
        observeUiState()
        observeSelectedDate()
    }

    override fun onResume() {
        super.onResume()
        // Corrects the shown day when the app has been left open across midnight.
        viewModel.onScreenResumed()
    }

    private fun setUpDateBar() =
        with(binding) {
            buttonPreviousDay.setOnClickListener { viewModel.onPreviousDayClicked() }
            buttonNextDay.setOnClickListener { viewModel.onNextDayClicked() }
            buttonJumpToToday.setOnClickListener { viewModel.onTodayClicked() }
            buttonPickDate.setOnClickListener { showDatePicker() }
        }

    private fun showDatePicker() {
        val today = viewModel.today()
        val constraints =
            CalendarConstraints.Builder()
                // Completing a day that has not happened yet is meaningless, so cap at today.
                .setValidator(DateValidatorPointBackward.before(today.plusDays(1).toUtcMillis()))
                .setEnd(today.toUtcMillis())
                .build()

        MaterialDatePicker.Builder
            .datePicker()
            .setTitleText(R.string.date_picker_title)
            .setCalendarConstraints(constraints)
            .setSelection(viewModel.selectedDate.value.toUtcMillis())
            .build()
            .apply {
                addOnPositiveButtonClickListener { millis ->
                    viewModel.onDatePicked(millis.toLocalDateFromUtc())
                }
            }.show(childFragmentManager, DATE_PICKER_TAG)
    }

    private fun observeSelectedDate() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedDate.collect(::renderSelectedDate)
            }
        }
    }

    private fun renderSelectedDate(date: LocalDate) =
        with(binding) {
            val today = viewModel.today()
            val label = DateLabelFormatter.format(requireContext(), date, today)

            buttonPickDate.text = label
            toolbar.title = label
            // Only shown off today, where it is the fastest way back.
            buttonJumpToToday.isVisible = date != today
            buttonNextDay.isEnabled = date.isBefore(today)
        }

    private fun setUpToolbarMenu() =
        with(binding.toolbar) {
            inflateMenu(R.menu.menu_habit_list)
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_all_habits -> {
                        findNavController().navigate(
                            HabitListFragmentDirections.actionHabitListToAllHabits(),
                        )
                        true
                    }

                    else -> false
                }
            }
        }

    private fun navigateToHabitEditor(habitId: Long) {
        findNavController().navigate(
            HabitListFragmentDirections.actionHabitListToAddEditHabit(habitId),
        )
    }

    /**
     * The app draws edge to edge, so keep content clear of the gesture bar. Only the bottom inset
     * is applied here; the AppBarLayout already consumes the top one via `fitsSystemWindows`.
     */
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

    override fun onDestroyView() {
        // The adapter outlives the view here, so drop the RecyclerView's reference to it.
        binding.recyclerHabits.adapter = null
        super.onDestroyView()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: UiState<List<HabitListItemUiModel>>) =
        with(binding) {
            progressLoading.isVisible = state is UiState.Loading
            recyclerHabits.isVisible = state is UiState.Success
            groupEmpty.isVisible = state is UiState.Empty
            groupError.isVisible = state is UiState.Error

            when (state) {
                is UiState.Success -> habitAdapter.submitList(state.data)

                is UiState.Empty -> {
                    habitAdapter.submitList(emptyList())
                    textEmptyTitle.setText(state.titleRes)
                    textEmptyMessage.setText(state.messageRes)
                }

                is UiState.Error -> textErrorMessage.text = state.throwable.localizedMessage
                UiState.Loading -> habitAdapter.submitList(emptyList())
            }
        }

    private fun showHabitMenu(
        item: HabitListItemUiModel,
        anchor: View,
    ) {
        PopupMenu(requireContext(), anchor).apply {
            inflate(R.menu.menu_habit_item)
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_habit_history -> {
                        findNavController().navigate(
                            HabitListFragmentDirections.actionHabitListToHabitDetail(item.id),
                        )
                        true
                    }

                    R.id.action_edit_habit -> {
                        navigateToHabitEditor(item.id)
                        true
                    }

                    R.id.action_archive_habit -> {
                        viewModel.onHabitArchived(item.id)
                        Snackbar.make(
                            binding.root,
                            getString(R.string.habit_archived_message, item.title),
                            Snackbar.LENGTH_SHORT,
                        ).setAnchorView(binding.fabAddHabit).show()
                        true
                    }

                    else -> false
                }
            }
        }.show()
    }

    private companion object {
        const val DATE_PICKER_TAG = "date_picker"
    }
}

/**
 * MaterialDatePicker works in UTC milliseconds, while the rest of the app uses [LocalDate].
 * These conversions are deliberately UTC-anchored on both sides so the round trip is lossless;
 * using the local zone here would shift the picked day by one either side of midnight.
 */
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateFromUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
