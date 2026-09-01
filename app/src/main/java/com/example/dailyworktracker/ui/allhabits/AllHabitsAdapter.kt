package com.example.dailyworktracker.ui.allhabits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.databinding.ItemAllHabitBinding

/**
 * Rows are drawn by `item_all_habit.xml` from the bound item; the only thing left here is the
 * overflow click, which needs the clicked view as a popup anchor and so cannot live in the layout.
 */
class AllHabitsAdapter(
    private val onMoreClicked: (item: AllHabitItemUiModel, anchor: View) -> Unit,
) : ListAdapter<AllHabitItemUiModel, AllHabitsAdapter.AllHabitViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): AllHabitViewHolder {
        val binding = ItemAllHabitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AllHabitViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AllHabitViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    inner class AllHabitViewHolder(
        private val binding: ItemAllHabitBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AllHabitItemUiModel) =
            with(binding) {
                this.item = item
                buttonMore.setOnClickListener { onMoreClicked(item, it) }
                // Data binding defers to the next frame by default, which shows the recycled row's
                // old contents for a frame while scrolling. Binding now keeps rows from flickering.
                executePendingBindings()
            }
    }

    private companion object {
        val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<AllHabitItemUiModel>() {
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
