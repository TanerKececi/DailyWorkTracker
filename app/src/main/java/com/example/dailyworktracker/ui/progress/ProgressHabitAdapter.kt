package com.example.dailyworktracker.ui.progress

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.databinding.ItemProgressHabitBinding

/**
 * The per-habit breakdown for the month on screen.
 *
 * Rows are drawn by `item_progress_habit.xml` from the bound item. What is left here is the tap
 * that opens the habit's own history, which the layout cannot express because the callback is not
 * the adapter's to own.
 */
class ProgressHabitAdapter(
    private val onHabitClicked: (habitId: Long) -> Unit,
) : ListAdapter<ProgressHabitUiModel, ProgressHabitAdapter.HabitViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): HabitViewHolder = HabitViewHolder(ItemProgressHabitBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(
        holder: HabitViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    inner class HabitViewHolder(
        private val binding: ItemProgressHabitBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ProgressHabitUiModel) =
            with(binding) {
                this.item = item
                root.setOnClickListener { onHabitClicked(item.id) }

                // Data binding defers to the next frame by default, which shows the recycled row's
                // old contents for a frame while scrolling. Binding now keeps rows from flickering.
                executePendingBindings()
            }
    }

    private companion object {
        val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<ProgressHabitUiModel>() {
                override fun areItemsTheSame(
                    oldItem: ProgressHabitUiModel,
                    newItem: ProgressHabitUiModel,
                ): Boolean = oldItem.id == newItem.id

                override fun areContentsTheSame(
                    oldItem: ProgressHabitUiModel,
                    newItem: ProgressHabitUiModel,
                ): Boolean = oldItem == newItem
            }
    }
}
