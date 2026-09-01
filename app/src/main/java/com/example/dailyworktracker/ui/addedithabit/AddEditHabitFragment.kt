package com.example.dailyworktracker.ui.addedithabit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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

    /**
     * Asked for the moment the user switches a reminder on, rather than at app start: the
     * request then has an obvious reason attached to it, and someone who never sets a
     * reminder is never asked at all.
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            renderReminderWarning(viewModel.uiState.value)
        }

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
        // Save is wired in the layout; the rest need a dialog, a dismiss, or a permission request.
        binding.viewModel = viewModel
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
                if (!isBindingState) this@AddEditHabitFragment.viewModel.onTitleChanged(text?.toString().orEmpty())
            }
            editEmoji.doAfterTextChanged { text ->
                if (!isBindingState) this@AddEditHabitFragment.viewModel.onEmojiChanged(text?.toString().orEmpty())
            }
            switchEveryDay.setOnCheckedChangeListener { _, isChecked ->
                if (!isBindingState) this@AddEditHabitFragment.viewModel.onEveryDayToggled(isChecked)
            }
            switchTrackAmount.setOnCheckedChangeListener { _, isChecked ->
                if (!isBindingState) {
                    this@AddEditHabitFragment.viewModel.onAmountTrackedChanged(isChecked)
                }
            }
            switchReminder.setOnCheckedChangeListener { _, isChecked ->
                if (isBindingState) return@setOnCheckedChangeListener
                this@AddEditHabitFragment.viewModel.onReminderEnabledChanged(isChecked)
                if (isChecked) requestNotificationPermissionIfNeeded()
            }
            buttonReminderTime.setOnClickListener { showTimePicker() }
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

    /**
     * Hands the state to the layout, which draws everything it can express on its own.
     *
     * What stays here is what XML cannot say: the day chips are built in code from
     * [DayOfWeek.entries], and the reminder warning depends on a runtime permission rather than on
     * state. The guard keeps those writes from being read back as user input.
     */
    private fun render(state: AddEditHabitScreenState) =
        with(binding) {
            if (state.isSaved) {
                dismiss()
                return@with
            }

            isBindingState = true

            binding.state = state
            // Bindings are applied now rather than next frame, so the guard below still covers them.
            executePendingBindings()

            chipGroupDays.children.filterIsInstance<Chip>().forEach { chip ->
                val day = chip.tag as DayOfWeek
                chip.isChecked = WeekdaySchedule.isScheduledOn(state.scheduleDaysBitmask, day)
            }

            renderReminderWarning(state)

            isBindingState = false
        }

    override fun onResume() {
        super.onResume()
        // Notifications may have been turned on or off in Settings while the sheet was open.
        renderReminderWarning(viewModel.uiState.value)
    }

    /**
     * The switch and the time button are bound from state; only this warning is not.
     *
     * Whether notifications can be posted is a runtime permission, which can change while the sheet
     * is open and is not something the ViewModel should be tracking.
     */
    private fun renderReminderWarning(state: AddEditHabitScreenState) {
        binding.textReminderWarning.isVisible =
            state.isReminderEnabled && !canPostNotifications()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (canPostNotifications()) return
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Below API 33 notifications need no grant, so there is nothing to ask for. */
    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TIME_PICKER_TAG = "reminder_time_picker"
    }
}
