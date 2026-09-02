package com.example.dailyworktracker.ui.habitlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.databinding.ItemHabitBinding
import com.example.dailyworktracker.databinding.ItemHabitSectionBinding

/**
 * Rows are drawn by `item_habit.xml` from the bound item. What is left here is the interaction
 * that the layout cannot express: callbacks the adapter does not own, and a popup that needs the
 * clicked view as its anchor.
 *
 * Section headings share this adapter rather than wrapping the list in something, so a habit moving
 * from In progress to Done is an ordinary list change that DiffUtil can animate.
 */
class HabitListAdapter(
    private val onToggleCompleted: (habitId: Long) -> Unit,
    private val onAmountClicked: (item: HabitListItemUiModel) -> Unit,
    private val onMoreClicked: (item: HabitListItemUiModel, anchor: View) -> Unit,
    private val onToggleSkipped: (item: HabitListItemUiModel) -> Unit,
) : ListAdapter<HabitListRow, RecyclerView.ViewHolder>(DIFF_CALLBACK) {
    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is HabitListRow.Header -> VIEW_TYPE_HEADER
            is HabitListRow.Habit -> VIEW_TYPE_HABIT
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemHabitSectionBinding.inflate(inflater, parent, false))
        } else {
            HabitViewHolder(ItemHabitBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val row = getItem(position)) {
            is HabitListRow.Header -> (holder as HeaderViewHolder).bind(row)
            is HabitListRow.Habit -> (holder as HabitViewHolder).bind(row.item)
        }
    }

    class HeaderViewHolder(
        private val binding: ItemHabitSectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: HabitListRow.Header) {
            binding.textSection.text =
                binding.root.context.getString(row.section.titleRes, row.count)
        }
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
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_HABIT = 1

        val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<HabitListRow>() {
                override fun areItemsTheSame(
                    oldItem: HabitListRow,
                    newItem: HabitListRow,
                ): Boolean =
                    when {
                        // A section keeps its identity as its count changes, so the heading updates
                        // in place instead of the whole section being replaced.
                        oldItem is HabitListRow.Header && newItem is HabitListRow.Header ->
                            oldItem.section == newItem.section

                        oldItem is HabitListRow.Habit && newItem is HabitListRow.Habit ->
                            oldItem.item.id == newItem.item.id

                        else -> false
                    }

                override fun areContentsTheSame(
                    oldItem: HabitListRow,
                    newItem: HabitListRow,
                ): Boolean = oldItem == newItem
            }
    }
}
