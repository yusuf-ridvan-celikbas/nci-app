package com.cansin.app.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import com.cansin.app.MainActivity
import com.cansin.app.R
import com.cansin.app.prefs.Settings
import com.cansin.app.system.AppLocale

class ReminderNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(settings: Settings, sessionId: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!manager.areNotificationsEnabled()) return
        val res = AppLocale.localized(context, settings.language)
        ensureChannels(res)
        val channel = if (settings.reminderSilent) CHANNEL_SILENT else CHANNEL
        val publicVersion = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.reminder_public))
            .build()
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(settings.appName.label))
            .setContentText(res.getString(R.string.reminder_text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .addAction(action(res.getString(R.string.action_another), ReminderReceiver.ACTION_ANOTHER, sessionId))
            .addAction(action(res.getString(R.string.action_snooze), ReminderReceiver.ACTION_SNOOZE, sessionId))
            .addAction(action(res.getString(R.string.action_stop), ReminderReceiver.ACTION_STOP, sessionId))
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun dismiss() = manager.cancel(NOTIFICATION_ID)

    private fun ensureChannels(res: Context) {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL, res.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_SILENT, res.getString(R.string.channel_reminders_silent), NotificationManager.IMPORTANCE_LOW),
            ).onEach { it.lockscreenVisibility = Notification.VISIBILITY_PRIVATE },
        )
    }

    private fun action(title: String, action: String, sessionId: Long) =
        Notification.Action.Builder(null as Icon?, title, ReminderReceiver.intent(context, action, sessionId)).build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL = "reminders"
        const val CHANNEL_SILENT = "reminders_silent"
    }
}
