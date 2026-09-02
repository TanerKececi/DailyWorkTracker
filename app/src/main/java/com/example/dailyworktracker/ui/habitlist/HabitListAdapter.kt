package com.example.dailyworktracker.ui.habitlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.databinding.ItemHabitBinding

/**
 * Rows are drawn by `item_habit.xml` from the bound item. What is left here is the interaction
 * that the layout cannot express: callbacks the adapter does not own, and a popup that needs the
 * clicked view as its anchor.
 */
class HabitListAdapter(
    private val onToggleCompleted: (habitId: Long) -> Unit,
    private val onAmountClicked: (item: HabitListItemUiModel) -> Unit,
    private val onMoreClicked: (item: HabitListItemUiModel, anchor: View) -> Unit,
    private val onToggleSkipped: (item: HabitListItemUiModel) -> Unit,
) : ListAdapter<HabitListItemUiModel, HabitListAdapter.HabitViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): HabitViewHolder {
        val binding = ItemHabitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HabitViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: HabitViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    inner class HabitViewHolder(
        private val binding: ItemHabitBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HabitListItemUiModel) =
            with(binding) {
                this.item = item

                // A click listener, not an OnCheckedChangeListener: the checked state is bound from
                // the item, and a change listener would fire on rebind and toggle whichever habit
                // previously occupied this recycled holder.
                checkboxCompleted.setOnClickListener { onToggleCompleted(item.id) }
                buttonAmount.setOnClickListener { onAmountClicked(item) }
                imageSkipped.setOnClickListener { onToggleSkipped(item) }
                // Tapping anywhere on the row does whatever that row's control does. A skipped row
                // shows the skip marker, so tapping it takes the skip back rather than silently
                // completing a day the user has already decided to pass on.
                root.setOnClickListener {
                    when {
                        item.isSkipped -> onToggleSkipped(item)
                        item.isAmountTracked -> onAmountClicked(item)
                        else -> onToggleCompleted(item.id)
                    }
                }
                buttonMore.setOnClickListener { onMoreClicked(item, it) }

                // Data binding defers to the next frame by default, which shows the recycled row's
                // old contents for a frame while scrolling. Binding now keeps rows from flickering.
                executePendingBindings()
            }
    }

    private companion object {
        val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<HabitListItemUiModel>() {
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
