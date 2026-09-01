package com.example.dailyworktracker.ui.habitdetail

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.R
import com.example.dailyworktracker.databinding.ItemHeatmapCellBinding
import com.example.dailyworktracker.databinding.ItemHeatmapMonthBinding
import com.google.android.material.color.MaterialColors
import java.time.Year
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Draws the day grid as a column of checkboxes: a day that was kept is filled, a day that was due
 * and missed is an empty outline, and a day nothing was due gets no box at all.
 *
 * Each row is a month gutter followed by seven days, which is why the grid is laid out in
 * [HabitDetailViewModel.COLUMNS] equal columns: the weekday header lines up with no span arithmetic.
 */
class HeatmapAdapter : ListAdapter<HeatmapItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {
    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is HeatmapItem.WeekGutter -> VIEW_TYPE_GUTTER
            is HeatmapItem.Day -> VIEW_TYPE_DAY
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_GUTTER) {
            GutterViewHolder(ItemHeatmapMonthBinding.inflate(inflater, parent, false))
        } else {
            DayViewHolder(ItemHeatmapCellBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val item = getItem(position)) {
            is HeatmapItem.WeekGutter -> (holder as GutterViewHolder).bind(item)
            is HeatmapItem.Day -> (holder as DayViewHolder).bind(item)
        }
    }

    class GutterViewHolder(
        private val binding: ItemHeatmapMonthBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HeatmapItem.WeekGutter) {
            val context = binding.root.context
            binding.containerBand.setBackgroundColor(bandColor(context, item.isAlternateMonth))
            binding.textMonth.text =
                item.month?.let { month ->
                    val name = month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    // Disambiguate once a grid spans a year boundary.
                    if (month.year == Year.now().value) name else name + " " + month.year
                }.orEmpty()
        }
    }

    class DayViewHolder(
        private val binding: ItemHeatmapCellBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HeatmapItem.Day) {
            val context = binding.root.context

            binding.containerBand.setBackgroundColor(bandColor(context, item.isAlternateMonth))

            // Out-of-range days keep their slot so the weekday columns stay aligned.
            binding.viewCell.isInvisible = item.status == DayStatus.OUT_OF_RANGE
            applyBox(context, item.status)

            binding.textDay.setTextColor(textColor(context, item.status))
            // An amount says more than the date, and the column plus the month gutter still place
            // the day. Cells with nothing logged keep showing the date.
            binding.textDay.text = (item.amount ?: item.date.dayOfMonth).toString()
            binding.root.contentDescription = describe(context, item)
        }

        /**
         * Filled for a kept day, outlined for a missed one.
         *
         * A day nothing was due on gets no box: drawing an empty one would read as an unticked
         * checkbox, which is the opposite of what it means.
         */
        private fun applyBox(
            context: Context,
            status: DayStatus,
        ) {
            val box: View = binding.viewCell
            when (status) {
                DayStatus.COMPLETED -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_filled)
                    box.backgroundTintList = ColorStateList.valueOf(attrColor(context, PRIMARY))
                }

                DayStatus.MISSED, DayStatus.OUT_OF_RANGE -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_outlined)
                    box.backgroundTintList = ColorStateList.valueOf(attrColor(context, OUTLINE))
                }

                // Due today: an empty box, emphasised so it reads as still open rather than missed.
                DayStatus.PENDING -> {
                    box.setBackgroundResource(R.drawable.bg_heatmap_day_outlined)
                    box.backgroundTintList = ColorStateList.valueOf(attrColor(context, PRIMARY))
                }

                DayStatus.NOT_SCHEDULED -> {
                    box.background = null
                    box.backgroundTintList = null
                }
            }
        }

        /** Day numbers sit on top of the box, so each one needs its own contrasting colour. */
        private fun textColor(
            context: Context,
            status: DayStatus,
        ): Int =
            when (status) {
                DayStatus.COMPLETED -> attrColor(context, ON_PRIMARY)
                DayStatus.PENDING -> attrColor(context, PRIMARY)
                else -> attrColor(context, ON_SURFACE_VARIANT)
            }

        private fun describe(
            context: Context,
            item: HeatmapItem.Day,
        ): String {
            val date = DATE_FORMAT.format(item.date)
            val state =
                when (item.status) {
                    DayStatus.COMPLETED -> context.getString(R.string.habit_detail_legend_done)
                    DayStatus.MISSED -> context.getString(R.string.habit_detail_legend_missed)
                    else -> ""
                }
            return if (state.isEmpty()) date else date + ", " + state
        }
    }

    companion object {
        private const val VIEW_TYPE_GUTTER = 0
        private const val VIEW_TYPE_DAY = 1

        private val PRIMARY = androidx.appcompat.R.attr.colorPrimary
        private val ON_PRIMARY = com.google.android.material.R.attr.colorOnPrimary
        private val OUTLINE = com.google.android.material.R.attr.colorOutline
        private val ON_SURFACE_VARIANT = com.google.android.material.R.attr.colorOnSurfaceVariant
        private val SURFACE_VARIANT = com.google.android.material.R.attr.colorSurfaceVariant

        /** Faint enough to group months without competing with the boxes drawn on top. */
        private const val BAND_ALPHA = 90

        private val DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

        fun attrColor(
            context: Context,
            attr: Int,
        ): Int = MaterialColors.getColor(context, attr, 0)

        fun bandColor(
            context: Context,
            isAlternateMonth: Boolean,
        ): Int =
            if (isAlternateMonth) {
                ColorUtils.setAlphaComponent(attrColor(context, SURFACE_VARIANT), BAND_ALPHA)
            } else {
                Color.TRANSPARENT
            }

        /** Tints for the legend, so the key cannot drift from the grid it explains. */
        fun completedTint(context: Context): Int = attrColor(context, PRIMARY)

        fun missedTint(context: Context): Int = attrColor(context, OUTLINE)

        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<HeatmapItem>() {
                override fun areItemsTheSame(
                    oldItem: HeatmapItem,
                    newItem: HeatmapItem,
                ): Boolean =
                    when {
                        oldItem is HeatmapItem.Day && newItem is HeatmapItem.Day ->
                            oldItem.date == newItem.date

                        oldItem is HeatmapItem.WeekGutter && newItem is HeatmapItem.WeekGutter ->
                            oldItem.weekStart == newItem.weekStart

                        else -> false
                    }

                override fun areContentsTheSame(
                    oldItem: HeatmapItem,
                    newItem: HeatmapItem,
                ): Boolean = oldItem == newItem
            }
    }
}
