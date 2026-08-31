package com.example.dailyworktracker.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.dailyworktracker.MainActivity
import com.example.dailyworktracker.R
import com.example.dailyworktracker.data.local.entity.Habit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the "this habit is still open today" notification.
 *
 * Kept apart from the worker so the worker is only about *when* to remind and this is only about
 * *how*, and so the channel is created in one place rather than wherever a notification happens to
 * be posted first.
 */
@Singleton
class HabitReminderNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val notificationManager = NotificationManagerCompat.from(context)

        fun notifyHabitDue(habit: Habit) {
            // Silently doing nothing is right here: the user declined notifications, and a reminder
            // is not worth an exception that would fail the worker and break the chain.
            if (!canPostNotifications()) return

            createChannel()
            notificationManager.notify(habit.id.toInt(), buildNotification(habit))
        }

        private fun buildNotification(habit: Habit): Notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_reminder)
                .setContentTitle(context.getString(R.string.reminder_title, habit.emoji, habit.title))
                .setContentText(context.getString(R.string.reminder_text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build()

        /**
         * Opens the app on the today screen. `FLAG_IMMUTABLE` is required from API 31 and costs
         * nothing here, since nothing needs to fill anything in on our behalf.
         */
        private fun openAppIntent(): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** Creating an existing channel is a no-op, so this needs no "have I already" bookkeeping. */
        private fun createChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.reminder_channel_description)
                },
            )
        }

        private fun canPostNotifications(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                notificationManager.areNotificationsEnabled()
            }

        private companion object {
            const val CHANNEL_ID = "habit_reminders"
        }
    }
