package com.example.dailyworktracker.ui.habitlist

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.R
import com.example.dailyworktracker.databinding.ItemDateStripDayBinding
import com.google.android.material.color.MaterialColors
import java.time.format.TextStyle
import java.util.Locale

/**
 * The horizontal strip of days above the list.
 *
 * Selection travels in the item rather than being held here, so changing day is an ordinary list
 * change: DiffUtil rebinds only the two days that actually moved.
 */
class DayStripAdapter(
    private val onDayClicked: (day: DayChip) -> Unit,
) : ListAdapter<DayChip, DayStripAdapter.DayViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): DayViewHolder = DayViewHolder(ItemDateStripDayBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(
        holder: DayViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    inner class DayViewHolder(
        private val binding: ItemDateStripDayBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(day: DayChip) {
            val context = binding.root.context

            // Two letters, as the mockup draws them: the one-letter NARROW form repeats itself
            // (T for Tuesday and Thursday), and the three-letter SHORT form crowds the strip.
            binding.textWeekday.text =
                day.date.dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    .take(WEEKDAY_LETTERS)
            binding.textDayNumber.text = day.date.dayOfMonth.toString()

            // Every day sits in a circle, as the mockup draws them. Selected is a filled disc;
            // today is a strong outline; the rest are faint, so the strip reads as a row of days
            // rather than as one day with neighbours.
            binding.textDayNumber.setBackgroundResource(
                if (day.isSelected) R.drawable.bg_day_selected else R.drawable.bg_day_today,
            )
            binding.textDayNumber.backgroundTintList =
                ColorStateList.valueOf(
                    when {
                        day.isSelected || day.isToday -> MaterialColors.getColor(binding.root, PRIMARY)
                        else -> MaterialColors.getColor(binding.root, OUTLINE_VARIANT)
                    },
                )
            binding.textDayNumber.setTextColor(
                MaterialColors.getColor(binding.root, if (day.isSelected) ON_PRIMARY else ON_SURFACE),
            )

            binding.root.setOnClickListener { onDayClicked(day) }
        }
    }

    private companion object {
        const val WEEKDAY_LETTERS = 2

        val PRIMARY = androidx.appcompat.R.attr.colorPrimary
        val ON_PRIMARY = com.google.android.material.R.attr.colorOnPrimary
        val ON_SURFACE = com.google.android.material.R.attr.colorOnSurface
        val OUTLINE_VARIANT = com.google.android.material.R.attr.colorOutlineVariant

        val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<DayChip>() {
                override fun areItemsTheSame(
                    oldItem: DayChip,
                    newItem: DayChip,
                ): Boolean = oldItem.date == newItem.date

                override fun areContentsTheSame(
                    oldItem: DayChip,
                    newItem: DayChip,
                ): Boolean = oldItem == newItem
            }
    }
}
