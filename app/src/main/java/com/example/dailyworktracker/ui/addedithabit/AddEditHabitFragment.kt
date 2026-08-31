package com.example.dailyworktracker.ui.addedithabit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.dailyworktracker.R
import com.example.dailyworktracker.databinding.BottomsheetAddEditHabitBinding
import com.example.dailyworktracker.util.WeekdaySchedule
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Creates or edits a habit.
 *
 * A bottom sheet rather than a full destination: the form is short, and staying in context keeps the
 * habit list visible behind it. If this grows (icon grid, reminder picker) it can be promoted to a
 * full-screen destination without touching the ViewModel.
 */
@AndroidEntryPoint
class AddEditHabitFragment : BottomSheetDialogFragment() {

    private var _binding: BottomsheetAddEditHabitBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: AddEditHabitViewModel by viewModels()

    /** Guards against the chip listeners firing while state is being written back into the views. */
    private var isBindingState = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomsheetAddEditHabitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpDayChips()
        setUpInputs()
        observeUiState()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setUpDayChips() {
        val inflater = LayoutInflater.from(requireContext())
        DayOfWeek.entries.forEach { day ->
            val chip = inflater.inflate(R.layout.item_day_chip, binding.chipGroupDays, false) as Chip
            chip.apply {
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                tag = day
                setOnCheckedChangeListener { _, isChecked ->
                    if (!isBindingState) viewModel.onDayToggled(day, isChecked)
                }
            }
            binding.chipGroupDays.addView(chip)
        }
    }

    private fun setUpInputs() = with(binding) {
        editTitle.doAfterTextChanged { text ->
            if (!isBindingState) viewModel.onTitleChanged(text?.toString().orEmpty())
        }
        editEmoji.doAfterTextChanged { text ->
            if (!isBindingState) viewModel.onEmojiChanged(text?.toString().orEmpty())
        }
        switchEveryDay.setOnCheckedChangeListener { _, isChecked ->
            if (!isBindingState) viewModel.onEveryDayToggled(isChecked)
        }
        buttonSave.setOnClickListener { viewModel.onSaveClicked() }
        buttonCancel.setOnClickListener { dismiss() }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: AddEditHabitUiState) = with(binding) {
        if (state.isSaved) {
            dismiss()
            return@with
        }

        isBindingState = true

        textSheetTitle.setText(
            if (state.isEditing) R.string.edit_habit_title else R.string.add_habit_title,
        )

        // Only write text back when it differs, otherwise the cursor jumps to the start on each keystroke.
        if (editTitle.text?.toString() != state.title) {
            editTitle.setText(state.title)
            editTitle.setSelection(state.title.length)
        }
        if (editEmoji.text?.toString() != state.emoji) {
            editEmoji.setText(state.emoji)
        }

        chipGroupDays.children.filterIsInstance<Chip>().forEach { chip ->
            val day = chip.tag as DayOfWeek
            chip.isChecked = WeekdaySchedule.isScheduledOn(state.scheduleDaysBitmask, day)
        }
        switchEveryDay.isChecked = WeekdaySchedule.isEveryDay(state.scheduleDaysBitmask)

        inputLayoutTitle.error = state.titleError?.let(::getString)
        textScheduleError.isVisible = state.scheduleError != null
        state.scheduleError?.let(textScheduleError::setText)

        buttonSave.isEnabled = !state.isSaving

        isBindingState = false
    }
}
