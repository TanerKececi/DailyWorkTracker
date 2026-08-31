package com.example.dailyworktracker.ui.allhabits

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
import com.example.dailyworktracker.databinding.FragmentAllHabitsBinding
import com.example.dailyworktracker.ui.common.UiState
import com.example.dailyworktracker.ui.common.viewBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Lists every habit, archived included, so habits that do not repeat today are still reachable for
 * editing, archiving and restoring.
 */
@AndroidEntryPoint
class AllHabitsFragment : Fragment(R.layout.fragment_all_habits) {
    private val binding by viewBinding(FragmentAllHabitsBinding::bind)
    private val viewModel: AllHabitsViewModel by viewModels()

    private val habitAdapter =
        AllHabitsAdapter(
            onMoreClicked = { item, anchor -> showHabitMenu(item, anchor) },
        )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        applyWindowInsets()
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.recyclerHabits.adapter = habitAdapter
        observeUiState()
    }

    override fun onDestroyView() {
        binding.recyclerHabits.adapter = null
        super.onDestroyView()
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

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: UiState<List<AllHabitItemUiModel>>) =
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
        item: AllHabitItemUiModel,
        anchor: View,
    ) {
        PopupMenu(requireContext(), anchor).apply {
            inflate(R.menu.menu_all_habit_item)
            menu.findItem(R.id.action_toggle_archive).setTitle(
                if (item.isArchived) R.string.habit_action_restore else R.string.habit_action_archive,
            )
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_habit_history -> {
                        findNavController().navigate(
                            AllHabitsFragmentDirections.actionAllHabitsToHabitDetail(item.id),
                        )
                        true
                    }

                    R.id.action_edit_habit -> {
                        findNavController().navigate(
                            AllHabitsFragmentDirections.actionAllHabitsToAddEditHabit(item.id),
                        )
                        true
                    }

                    R.id.action_toggle_archive -> {
                        viewModel.onArchiveToggled(item.id, item.isArchived)
                        val message =
                            if (item.isArchived) {
                                R.string.habit_restored_message
                            } else {
                                R.string.habit_archived_message
                            }
                        Snackbar.make(
                            binding.root,
                            getString(message, item.title),
                            Snackbar.LENGTH_SHORT,
                        ).show()
                        true
                    }

                    else -> false
                }
            }
        }.show()
    }
}
