package com.cansin.app.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cansin.app.CansinApp
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.takeIf { it in ACTIONS } ?: return
        val sessionId = intent.getLongExtra(EXTRA_SESSION, -1L).takeIf { it > 0 } ?: return
        val app = context.applicationContext as CansinApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.reminders.handle(action, sessionId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.cansin.app.reminder.REMIND"
        const val ACTION_ANOTHER = "com.cansin.app.reminder.ANOTHER"
        const val ACTION_SNOOZE = "com.cansin.app.reminder.SNOOZE"
        const val ACTION_STOP = "com.cansin.app.reminder.STOP"
        private val ACTIONS = setOf(ACTION_REMIND, ACTION_ANOTHER, ACTION_SNOOZE, ACTION_STOP)
        private const val EXTRA_SESSION = "session_id"

        fun intent(context: Context, action: String, sessionId: Long): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_SESSION, sessionId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
