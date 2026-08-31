package com.example.dailyworktracker.ui.habitlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.R
import com.example.dailyworktracker.databinding.ItemHabitBinding
import com.example.dailyworktracker.ui.common.ScheduleFormatter

/**
 * Renders the habit rows. Row interactions are surfaced as callbacks rather than handled here, so
 * the adapter stays free of navigation and repository concerns.
 */
class HabitListAdapter(
    private val onToggleCompleted: (habitId: Long) -> Unit,
    private val onMoreClicked: (item: HabitListItemUiModel, anchor: android.view.View) -> Unit,
) : ListAdapter<HabitListItemUiModel, HabitListAdapter.HabitViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        val binding = ItemHabitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HabitViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HabitViewHolder(
        private val binding: ItemHabitBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HabitListItemUiModel) = with(binding) {
            val context = root.context
            textEmoji.text = item.emoji
            textTitle.text = item.title
            textSchedule.text = ScheduleFormatter.format(context, item.scheduleDaysBitmask)

            // Detach the listener before setting state, otherwise recycling a view fires a spurious
            // toggle for whichever habit previously occupied this holder.
            checkboxCompleted.setOnCheckedChangeListener(null)
            checkboxCompleted.isChecked = item.isCompleted
            checkboxCompleted.contentDescription =
                context.getString(R.string.habit_completed_checkbox, item.title)
            checkboxCompleted.setOnCheckedChangeListener { _, _ -> onToggleCompleted(item.id) }

            buttonMore.contentDescription =
                context.getString(R.string.habit_list_more_options, item.title)
            buttonMore.setOnClickListener { onMoreClicked(item, it) }

            // Tapping anywhere on the row is the primary action: check it off.
            root.setOnClickListener { checkboxCompleted.isChecked = !checkboxCompleted.isChecked }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<HabitListItemUiModel>() {
            override fun areItemsTheSame(
                oldItem: HabitListItemUiModel,
                newItem: HabitListItemUiModel,
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: HabitListItemUiModel,
                newItem: HabitListItemUiModel,
            ): Boolean = oldItem == newItem
        }
    }
}
