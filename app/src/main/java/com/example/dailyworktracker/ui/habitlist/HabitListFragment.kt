package com.example.dailyworktracker.ui.habitlist

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.dailyworktracker.BuildConfig
import com.example.dailyworktracker.R
import com.example.dailyworktracker.data.sample.SampleDataSeeder
import com.example.dailyworktracker.databinding.FragmentHabitListBinding
import com.example.dailyworktracker.ui.addedithabit.AddEditHabitViewModel.Companion.NEW_HABIT_ID
import com.example.dailyworktracker.ui.common.viewBinding
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

/** Shows the habits due on the selected day and lets the user tick them off, today or in the past. */
@AndroidEntryPoint
class HabitListFragment : Fragment(R.layout.fragment_habit_list) {
    private val binding by viewBinding(FragmentHabitListBinding::bind)
    private val viewModel: HabitListViewModel by viewModels()

    /** Only ever used behind a `BuildConfig.DEBUG` check; see [seedSampleData]. */
    @Inject
    lateinit var sampleDataSeeder: SampleDataSeeder

    private val habitAdapter =
        HabitListAdapter(
            onToggleCompleted = { habitId -> viewModel.onHabitCheckedChanged(habitId) },
            onAmountClicked = { item -> showAmountDialog(item) },
            onMoreClicked = { item, anchor -> showHabitMenu(item, anchor) },
            onToggleSkipped = { item -> toggleSkip(item) },
        )

    /**
     * Skips the day on screen, or takes the skip back, and says which happened.
     *
     * The Snackbar reports the state the row has just moved *to*, read from the item as it was
     * before the toggle. Undo is the same call again, because a skip is its own opposite.
     */
    private fun toggleSkip(item: HabitListItemUiModel) {
        viewModel.onHabitSkipToggled(item.id)
        val message =
            if (item.isSkipped) R.string.habit_unskipped_message else R.string.habit_skipped_message
        Snackbar.make(
            binding.root,
            getString(message, item.title),
            Snackbar.LENGTH_SHORT,
        ).setAnchorView(binding.fabAddHabit)
            .setAction(R.string.habit_skip_undo) { viewModel.onHabitSkipToggled(item.id) }
            .show()
    }

    /**
     * Asks how much was done, prefilled with whatever is already recorded.
     *
     * Built here rather than as a DialogFragment because it holds nothing worth restoring: the only
     * state is the number in the field, and a rotation mid-entry losing it is a smaller cost than a
     * second fragment with its own lifecycle.
     */
    private fun showAmountDialog(item: HabitListItemUiModel) {
        val input =
            EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(item.amount?.toString().orEmpty())
                setSelection(text.length)
            }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.habit_amount_dialog_title, item.title))
            .setView(input, DIALOG_PADDING, DIALOG_PADDING, DIALOG_PADDING, 0)
            .setNegativeButton(R.string.add_habit_cancel, null)
            .setPositiveButton(R.string.add_habit_save) { _, _ ->
                // A blank field means "nothing done", which clears the day rather than doing nothing.
                viewModel.onAmountEntered(item.id, input.text.toString().toIntOrNull() ?: 0)
            }.show()
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        applyWindowInsets()
        setUpToolbarMenu()
        // The date bar's stepper buttons are wired in the layout; only the picker needs a dialog.
        binding.viewModel = viewModel
        binding.buttonPickDate.setOnClickListener { showDatePicker() }
        binding.recyclerHabits.adapter = habitAdapter
        // The default change animation cross-fades a rebound row by animating its alpha and its
        // translation, which fights both the dimmed look of a skipped row and the swipe reset
        // below. Ticking a box is not a change worth animating anyway.
        (binding.recyclerHabits.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        ItemTouchHelper(SkipSwipeCallback(::toggleSkip)).attachToRecyclerView(binding.recyclerHabits)
        binding.fabAddHabit.setOnClickListener { navigateToHabitEditor(NEW_HABIT_ID) }
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        // Corrects the shown day when the app has been left open across midnight.
        viewModel.onScreenResumed()
    }

    private fun showDatePicker() {
        val state = viewModel.uiState.value
        val today = state.today
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
            .setSelection(state.selectedDate.toUtcMillis())
            .build()
            .apply {
                addOnPositiveButtonClickListener { millis ->
                    viewModel.onDatePicked(millis.toLocalDateFromUtc())
                }
            }.show(childFragmentManager, DATE_PICKER_TAG)
    }

    private fun setUpToolbarMenu() =
        with(binding.toolbar) {
            inflateMenu(R.menu.menu_habit_list)
            // Sample data is a development aid; it must never be reachable in a release build.
            menu.findItem(R.id.action_seed_sample_data).isVisible = BuildConfig.DEBUG

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_all_habits -> {
                        findNavController().navigate(
                            HabitListFragmentDirections.actionHabitListToAllHabits(),
                        )
                        true
                    }

                    R.id.action_seed_sample_data -> {
                        seedSampleData()
                        true
                    }

                    else -> false
                }
            }
        }

    /**
     * Debug-only development aid.
     *
     * Deliberately driven from the Fragment rather than the ViewModel: the seeder is not part of the
     * app's behaviour, and threading it through the production ViewModel would put a debug
     * dependency in its constructor and in every test that builds one.
     */
    private fun seedSampleData() {
        viewLifecycleOwner.lifecycleScope.launch {
            sampleDataSeeder.seed()
            viewModel.onTodayClicked()
            Snackbar.make(
                binding.root,
                R.string.debug_sample_data_inserted,
                Snackbar.LENGTH_SHORT,
            ).setAnchorView(binding.fabAddHabit).show()
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

    /**
     * The layout renders itself from the state, including the date bar; this only hands each new
     * value over.
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

        /** Keeps the dialog's bare EditText off the dialog edges, in pixels. */
        const val DIALOG_PADDING = 48
    }
}

/**
 * MaterialDatePicker works in UTC milliseconds, while the rest of the app uses [LocalDate].
 * These conversions are deliberately UTC-anchored on both sides so the round trip is lossless;
 * using the local zone here would shift the picked day by one either side of midnight.
 */
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateFromUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
