package com.example.dailyworktracker.ui.allhabits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.R
import com.example.dailyworktracker.databinding.ItemAllHabitBinding
import com.example.dailyworktracker.ui.common.ScheduleFormatter

class AllHabitsAdapter(
    private val onMoreClicked: (item: AllHabitItemUiModel, anchor: View) -> Unit,
) : ListAdapter<AllHabitItemUiModel, AllHabitsAdapter.AllHabitViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AllHabitViewHolder {
        val binding = ItemAllHabitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AllHabitViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AllHabitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AllHabitViewHolder(
        private val binding: ItemAllHabitBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AllHabitItemUiModel) = with(binding) {
            val context = root.context
            textEmoji.text = item.emoji
            textTitle.text = item.title
            textSchedule.text = ScheduleFormatter.format(context, item.scheduleDaysBitmask)
            chipArchived.isVisible = item.isArchived

            // Dim archived habits so the distinction reads at a glance, not just from the badge.
            root.alpha = if (item.isArchived) ARCHIVED_ALPHA else 1f

            buttonMore.contentDescription =
                context.getString(R.string.habit_list_more_options, item.title)
            buttonMore.setOnClickListener { onMoreClicked(item, it) }
        }
    }

    private companion object {
        const val ARCHIVED_ALPHA = 0.55f

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AllHabitItemUiModel>() {
            override fun areItemsTheSame(
                oldItem: AllHabitItemUiModel,
                newItem: AllHabitItemUiModel,
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: AllHabitItemUiModel,
                newItem: AllHabitItemUiModel,
            ): Boolean = oldItem == newItem
        }
    }
}
