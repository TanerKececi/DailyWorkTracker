package com.example.dailyworktracker.ui.habitlist

import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.dailyworktracker.R
import com.google.android.material.color.MaterialColors

/**
 * Swiping a row left marks the day it shows as deliberately skipped.
 *
 * A toggle, not a dismissal: the row stays in the list, so the same gesture on a skipped row takes
 * the skip away again.
 *
 * That is why the swipe is deliberately never allowed to *complete*. A completed swipe leaves the
 * view translated off screen, because ItemTouchHelper assumes the row is about to be removed and
 * only restores it once the view detaches from the window - which never happens when the row is
 * merely rebound in place. So both thresholds are set out of reach, the row always springs back,
 * and the action is fired from [clearView] once it has.
 */
class SkipSwipeCallback(
    private val onSkipSwiped: (item: HabitListItemUiModel) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
    /** Set while dragging, read once the row has settled back. */
    private var isFarEnough = false

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    /** A section heading has no day to skip, so it does not move under the finger at all. */
    override fun getSwipeDirs(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ): Int = if (rowAt(viewHolder) is HabitListRow.Habit) super.getSwipeDirs(recyclerView, viewHolder) else 0

    // Out of reach on purpose: see the class comment. Distance and flick speed both have to miss.
    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = Float.MAX_VALUE

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = Float.MAX_VALUE

    /** Unreachable while the thresholds above cannot be met, and nothing should depend on it. */
    override fun onSwiped(
        viewHolder: RecyclerView.ViewHolder,
        direction: Int,
    ) = Unit

    override fun onSelectedChanged(
        viewHolder: RecyclerView.ViewHolder?,
        actionState: Int,
    ) {
        super.onSelectedChanged(viewHolder, actionState)
        // A new gesture starts from scratch, or an abandoned one would fire on the next row.
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) isFarEnough = false
    }

    override fun clearView(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ) {
        super.clearView(recyclerView, viewHolder)
        if (!isFarEnough) return
        isFarEnough = false

        val row = rowAt(viewHolder) as? HabitListRow.Habit ?: return
        onSkipSwiped(row.item)
    }

    /** Null when the holder has no current position, which happens mid-update. */
    private fun rowAt(viewHolder: RecyclerView.ViewHolder): HabitListRow? {
        val adapter = viewHolder.bindingAdapter as? HabitListAdapter ?: return null
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return null
        return adapter.currentList.getOrNull(position)
    }

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        // Only a leftward swipe means anything, and a settled row has nothing behind it to show.
        if (dX < 0f) {
            val row = viewHolder.itemView
            // Only while the finger is down: the spring back must not re-arm the action.
            if (isCurrentlyActive) isFarEnough = -dX >= row.width * TRIGGER_FRACTION

            val background =
                ColorDrawable(
                    MaterialColors.getColor(row, com.google.android.material.R.attr.colorSecondaryContainer),
                )
            background.setBounds(row.right + dX.toInt(), row.top, row.right, row.bottom)
            background.draw(canvas)

            val icon = AppCompatResources.getDrawable(recyclerView.context, R.drawable.ic_skip)
            if (icon != null) {
                DrawableCompat.setTint(
                    icon,
                    MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSecondaryContainer),
                )
                val top = row.top + (row.height - icon.intrinsicHeight) / 2
                val right = row.right - ICON_MARGIN_PX
                val left = right - icon.intrinsicWidth
                // Skip drawing rather than clipping: a half-swipe should not show half an icon.
                if (left > row.right + dX) {
                    icon.setBounds(left, top, right, top + icon.intrinsicHeight)
                    icon.draw(canvas)
                }
            }
        }

        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private companion object {
        /** How far across the row the swipe has to travel to count. */
        const val TRIGGER_FRACTION = 0.4f
        const val ICON_MARGIN_PX = 48
    }
}
