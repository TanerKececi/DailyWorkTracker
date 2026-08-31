package com.example.dailyworktracker.ui.habitdetail

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.R
import com.example.dailyworktracker.databinding.ItemHeatmapCellBinding
import com.example.dailyworktracker.databinding.ItemHeatmapMonthBinding
import com.google.android.material.color.MaterialColors
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Draws the day grid.
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
            val month = item.month
            binding.textMonth.text =
                month?.let {
                    val name = it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    // Disambiguate once a grid spans a year boundary.
                    if (it.year == java.time.Year.now().value) name else "$name ${it.year}"
                }.orEmpty()
        }
    }

    class DayViewHolder(
        private val binding: ItemHeatmapCellBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HeatmapItem.Day) {
            val context = binding.root.context

            // Out-of-range days keep their slot so the weekday columns stay aligned.
            binding.viewCell.isInvisible = item.status == DayStatus.OUT_OF_RANGE
            binding.viewCell.backgroundTintList =
                ColorStateList.valueOf(fillColor(context, item.status))
            binding.textDay.setTextColor(textColor(context, item.status))
            binding.textDay.text = item.date.dayOfMonth.toString()

            binding.root.contentDescription = describe(context, item)
        }

        private fun fillColor(
            context: Context,
            status: DayStatus,
        ): Int =
            when (status) {
                DayStatus.COMPLETED -> attrColor(context, androidx.appcompat.R.attr.colorPrimary)
                DayStatus.MISSED -> attrColor(context, com.google.android.material.R.attr.colorErrorContainer)
                // Clearly tinted, not another shade of grey: today is still due, not an off-day.
                DayStatus.PENDING -> attrColor(context, com.google.android.material.R.attr.colorPrimaryContainer)
                DayStatus.NOT_SCHEDULED, DayStatus.OUT_OF_RANGE ->
                    attrColor(context, com.google.android.material.R.attr.colorSurfaceVariant)
            }

        /** Day numbers sit on top of the fill, so each one needs its own contrasting colour. */
        private fun textColor(
            context: Context,
            status: DayStatus,
        ): Int =
            when (status) {
                DayStatus.COMPLETED -> attrColor(context, com.google.android.material.R.attr.colorOnPrimary)
                DayStatus.MISSED -> attrColor(context, com.google.android.material.R.attr.colorOnErrorContainer)
                DayStatus.PENDING -> attrColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer)
                DayStatus.NOT_SCHEDULED, DayStatus.OUT_OF_RANGE ->
                    attrColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
            }

        private fun attrColor(
            context: Context,
            attr: Int,
        ): Int = MaterialColors.getColor(context, attr, 0)

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
            return if (state.isEmpty()) date else "$date, $state"
        }
    }

    private companion object {
        const val VIEW_TYPE_GUTTER = 0
        const val VIEW_TYPE_DAY = 1

        val DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

        val DIFF_CALLBACK =
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
