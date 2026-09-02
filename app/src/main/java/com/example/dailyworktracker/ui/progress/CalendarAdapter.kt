package com.example.dailyworktracker.ui.progress

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.R
import com.example.dailyworktracker.databinding.ItemCalendarDayBinding
import com.example.dailyworktracker.ui.common.DayStatus
import com.example.dailyworktracker.ui.habitdetail.HeatmapAdapter
import com.google.android.material.color.MaterialColors

/**
 * One month as a seven-column grid.
 *
 * Boxes use the same drawables and the same tints as the detail heatmap, so a filled square means
 * the same thing on both screens. The one addition is [DayStatus.PARTIAL], which only arises here:
 * a single habit's day is done or it is not, but a day can hold several habits.
 */
class CalendarAdapter : ListAdapter<CalendarDay, CalendarAdapter.DayViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): DayViewHolder = DayViewHolder(ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(
        holder: DayViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class DayViewHolder(
        private val binding: ItemCalendarDayBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CalendarDay) {
            val context = binding.root.context
            val box: View = binding.viewCell

            // A leading blank keeps its slot so the dates stay under the right weekday.
            binding.root.isInvisible = item.date == null
            binding.textDay.text = item.date?.dayOfMonth?.toString().orEmpty()

            when (item.status) {
                DayStatus.COMPLETED -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_filled)
                    box.backgroundTintList =
                        ColorStateList.valueOf(HeatmapAdapter.completedTint(context))
                }

                // Some of the day was kept: the same filled box, softened, so it reads as progress
                // rather than as either a clean sweep or a failure.
                DayStatus.PARTIAL -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_filled)
                    box.backgroundTintList =
                        ColorStateList.valueOf(
                            MaterialColors.compositeARGBWithAlpha(
                                HeatmapAdapter.completedTint(context),
                                PARTIAL_ALPHA,
                            ),
                        )
                }

                DayStatus.SKIPPED -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_filled)
                    box.backgroundTintList =
                        ColorStateList.valueOf(HeatmapAdapter.skippedTint(context))
                }

                // Due today: an empty box, emphasised so it reads as still open rather than missed.
                DayStatus.PENDING -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_outlined)
                    box.backgroundTintList =
                        ColorStateList.valueOf(HeatmapAdapter.completedTint(context))
                }

                DayStatus.MISSED, DayStatus.OUT_OF_RANGE -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_outlined)
                    box.backgroundTintList =
                        ColorStateList.valueOf(HeatmapAdapter.missedTint(context))
                }

                // Nothing was due: no box at all. An empty one would read as an unticked checkbox,
                // which is the opposite of what it means.
                DayStatus.NOT_SCHEDULED -> {
                    box.background = null
                    box.backgroundTintList = null
                }
            }

            binding.textDay.setTextColor(
                when (item.status) {
                    DayStatus.COMPLETED -> MaterialColors.getColor(box, ON_PRIMARY)
                    else -> MaterialColors.getColor(box, ON_SURFACE_VARIANT)
                },
            )
        }
    }

    private companion object {
        /** Visibly lighter than a full day, still clearly a filled box rather than an outline. */
        const val PARTIAL_ALPHA = 110

        val ON_PRIMARY = com.google.android.material.R.attr.colorOnPrimary
        val ON_SURFACE_VARIANT = com.google.android.material.R.attr.colorOnSurfaceVariant

        val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<CalendarDay>() {
                override fun areItemsTheSame(
                    oldItem: CalendarDay,
                    newItem: CalendarDay,
                ): Boolean = oldItem.date == newItem.date

                override fun areContentsTheSame(
                    oldItem: CalendarDay,
                    newItem: CalendarDay,
                ): Boolean = oldItem == newItem
            }
    }
}
