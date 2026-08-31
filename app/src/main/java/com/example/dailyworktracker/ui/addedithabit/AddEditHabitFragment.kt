package com.example.dailyworktracker.ui.addedithabit

import android.os.Bundle
import android.text.format.DateFormat
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
import com.example.dailyworktracker.ui.common.TimeFormatter
import com.example.dailyworktracker.util.WeekdaySchedule
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Creates or edits a habit.
 *
 * A bottom sheet rather than a full destination: the form is short, and staying in context keeps the
 * habit list visible behind it. If this grows (icon grid, more scheduling options) it can be
 * promoted to a full-screen destination without touching the ViewModel.
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

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setUpDayChips()
        setUpInputs()
        reconnectTimePicker()
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

    private fun setUpInputs() =
        with(binding) {
            editTitle.doAfterTextChanged { text ->
                if (!isBindingState) viewModel.onTitleChanged(text?.toString().orEmpty())
            }
            editEmoji.doAfterTextChanged { text ->
                if (!isBindingState) viewModel.onEmojiChanged(text?.toString().orEmpty())
            }
            switchEveryDay.setOnCheckedChangeListener { _, isChecked ->
                if (!isBindingState) viewModel.onEveryDayToggled(isChecked)
            }
            switchReminder.setOnCheckedChangeListener { _, isChecked ->
                if (!isBindingState) viewModel.onReminderEnabledChanged(isChecked)
            }
            buttonReminderTime.setOnClickListener { showTimePicker() }
            buttonSave.setOnClickListener { viewModel.onSaveClicked() }
            buttonCancel.setOnClickListener { dismiss() }
        }

    private fun showTimePicker() {
        val current = viewModel.uiState.value.reminderTime
        MaterialTimePicker.Builder()
            .setTitleText(R.string.add_habit_reminder_picker_title)
            // Follow the device clock setting rather than forcing one on the user.
            .setTimeFormat(
                if (DateFormat.is24HourFormat(requireContext())) {
                    TimeFormat.CLOCK_24H
                } else {
                    TimeFormat.CLOCK_12H
                },
            )
            .setHour(current.hour)
            .setMinute(current.minute)
            .build()
            .also(::listenToTimePicker)
            .show(childFragmentManager, TIME_PICKER_TAG)
    }

    /**
     * A dialog fragment outlives the listener that created it: after a rotation the fragment manager
     * restores the picker but its callback is gone, so confirming it would silently do nothing.
     * Re-attaching on every view creation closes that gap.
     */
    private fun reconnectTimePicker() {
        val restored = childFragmentManager.findFragmentByTag(TIME_PICKER_TAG) as? MaterialTimePicker
        restored?.let(::listenToTimePicker)
    }

    private fun listenToTimePicker(picker: MaterialTimePicker) {
        picker.addOnPositiveButtonClickListener {
            viewModel.onReminderTimeChanged(LocalTime.of(picker.hour, picker.minute))
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: AddEditHabitUiState) =
        with(binding) {
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

            renderReminder(state.isReminderEnabled, state.reminderTime)

            inputLayoutTitle.error = state.titleError?.let(::getString)
            textScheduleError.isVisible = state.scheduleError != null
            state.scheduleError?.let(textScheduleError::setText)

            buttonSave.isEnabled = !state.isSaving

            isBindingState = false
        }

    private fun renderReminder(
        isEnabled: Boolean,
        time: LocalTime,
    ) = with(binding) {
        switchReminder.isChecked = isEnabled
        buttonReminderTime.isVisible = isEnabled

        val formatted = TimeFormatter.format(requireContext(), time)
        buttonReminderTime.text = formatted
        buttonReminderTime.contentDescription =
            getString(R.string.add_habit_reminder_time_description, formatted)
    }

    private companion object {
        const val TIME_PICKER_TAG = "reminder_time_picker"
    }
}
