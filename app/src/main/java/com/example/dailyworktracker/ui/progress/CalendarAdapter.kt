package com.example.dailyworktracker.ui.progress

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.databinding.ItemCalendarDayBinding
import com.example.dailyworktracker.ui.common.DayStatus
import com.example.dailyworktracker.ui.habitdetail.HeatmapAdapter
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

/**
 * One month as a seven-column grid of rings.
 *
 * A ring rather than a filled box, because a day here covers every habit at once: the arc shows how
 * much of the day was kept. The detail heatmap keeps its boxes, where a single habit's day really is
 * one thing or the other, so the two grids look deliberately different rather than accidentally so.
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
            val ring = binding.ringDay

            // A leading blank keeps its slot so the dates stay under the right weekday.
            binding.root.isInvisible = item.date == null
            binding.textDay.text = item.date?.dayOfMonth?.toString().orEmpty()

            // A day that has not happened yet keeps its number and loses its ring entirely, so the
            // rest of the month cannot read as already failed.
            ring.isInvisible = item.status == DayStatus.OUT_OF_RANGE

            ring.progress = (item.fraction * PERCENT).roundToInt()
            ring.setIndicatorColor(indicatorColor(item.status, context))
            ring.trackColor =
                MaterialColors.compositeARGBWithAlpha(
                    HeatmapAdapter.missedTint(context),
                    TRACK_ALPHA,
                )

            binding.textDay.setTextColor(
                when (item.status) {
                    // A day nothing was due on is present but unremarkable; everything else is read.
                    DayStatus.NOT_SCHEDULED, DayStatus.OUT_OF_RANGE ->
                        MaterialColors.compositeARGBWithAlpha(
                            MaterialColors.getColor(ring, ON_SURFACE_VARIANT),
                            MUTED_TEXT_ALPHA,
                        )

                    else -> MaterialColors.getColor(ring, ON_SURFACE)
                },
            )
        }

        /**
         * The arc's colour.
         *
         * Skipped is muted rather than absent: the day was handled, so it should not look like a
         * day that simply never came up. Everything else uses the same accent the detail heatmap
         * fills a completed day with, so the two screens agree about what "done" looks like.
         */
        private fun indicatorColor(
            status: DayStatus,
            context: android.content.Context,
        ): Int =
            when (status) {
                DayStatus.SKIPPED -> HeatmapAdapter.skippedTint(context)
                else -> HeatmapAdapter.completedTint(context)
            }
    }

    private companion object {
        const val PERCENT = 100

        /** Faint enough that an empty ring reads as an outline rather than as a filled shape. */
        const val TRACK_ALPHA = 90
        const val MUTED_TEXT_ALPHA = 110

        val ON_SURFACE = com.google.android.material.R.attr.colorOnSurface
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
