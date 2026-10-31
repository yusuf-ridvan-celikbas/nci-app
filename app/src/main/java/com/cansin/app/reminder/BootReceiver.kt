package com.cansin.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cansin.app.CansinApp
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as CansinApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.reminders.restore()
            } finally {
                pending.finish()
            }
        }
    }
}
