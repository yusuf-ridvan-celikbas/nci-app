package com.cansin.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cansin.app.CansinApp
import kotlinx.coroutines.launch

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ADD) return
        val app = context.applicationContext as CansinApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.sessionRepository.addBeer()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ADD = "com.cansin.app.widget.ADD"
    }
}
