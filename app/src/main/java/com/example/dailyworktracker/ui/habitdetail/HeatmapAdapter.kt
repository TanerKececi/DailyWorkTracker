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
import com.google.android.material.color.MaterialColors
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Draws the day grid. One row is one week, so the weekday header above it lines up. */
class HeatmapAdapter :
    ListAdapter<HeatmapCellUiModel, HeatmapAdapter.CellViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): CellViewHolder {
        val binding = ItemHeatmapCellBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CellViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: CellViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class CellViewHolder(
        private val binding: ItemHeatmapCellBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HeatmapCellUiModel) {
            val context = binding.root.context

            // Out-of-range days keep their slot so the weekday columns stay aligned.
            binding.viewCell.isInvisible = item.status == DayStatus.OUT_OF_RANGE
            binding.viewCell.backgroundTintList =
                ColorStateList.valueOf(colorFor(context, item.status))

            binding.root.contentDescription = describe(context, item)
        }

        private fun colorFor(
            context: Context,
            status: DayStatus,
        ): Int =
            when (status) {
                DayStatus.COMPLETED -> attrColor(context, androidx.appcompat.R.attr.colorPrimary)
                DayStatus.MISSED -> attrColor(context, com.google.android.material.R.attr.colorErrorContainer)
                DayStatus.PENDING -> attrColor(context, com.google.android.material.R.attr.colorSecondaryContainer)
                DayStatus.NOT_SCHEDULED, DayStatus.OUT_OF_RANGE ->
                    attrColor(context, com.google.android.material.R.attr.colorSurfaceVariant)
            }

        private fun attrColor(
            context: Context,
            attr: Int,
        ): Int = MaterialColors.getColor(context, attr, 0)

        private fun describe(
            context: Context,
            item: HeatmapCellUiModel,
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
        val DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

        val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<HeatmapCellUiModel>() {
                override fun areItemsTheSame(
                    oldItem: HeatmapCellUiModel,
                    newItem: HeatmapCellUiModel,
                ): Boolean = oldItem.date == newItem.date

                override fun areContentsTheSame(
                    oldItem: HeatmapCellUiModel,
                    newItem: HeatmapCellUiModel,
                ): Boolean = oldItem == newItem
            }
    }
}
