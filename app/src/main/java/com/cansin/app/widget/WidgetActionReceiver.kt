package com.cansin.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.cansin.app.CansinApp
import com.cansin.app.domain.TimerRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.takeIf { it in ACTIONS } ?: return
        val app = context.applicationContext as CansinApp
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val known = AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, CounterWidget::class.java))
        if (widgetId !in known) return
        val pending = goAsync()
        app.appScope.launch {
            try {
                when (action) {
                    ACTION_ADD -> app.sessionRepository.addBeer()
                    ACTION_WATER -> app.sessionRepository.addWater()
                    ACTION_TIMER -> {
                        val running = TimerRules.isCurrent(app.settingsStore.timerState.first(), app.sessionRepository.snapshot().sessionId)
                        if (running) app.drinkTimer.stop() else app.drinkTimer.start()
                    }
                    ACTION_PREV, ACTION_NEXT -> {
                        val page = TimerRules.page(app.settingsStore.widgetPage(widgetId))
                        val next = TimerRules.step(page, if (action == ACTION_NEXT) 1 else -1)
                        app.settingsStore.setWidgetPage(widgetId, next.ordinal)
                    }
                }
                CounterWidget.refreshAll(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ADD = "com.cansin.app.widget.ADD"
        const val ACTION_WATER = "com.cansin.app.widget.WATER"
        const val ACTION_TIMER = "com.cansin.app.widget.TIMER"
        const val ACTION_PREV = "com.cansin.app.widget.PREV"
        const val ACTION_NEXT = "com.cansin.app.widget.NEXT"
        private val ACTIONS = listOf(ACTION_ADD, ACTION_WATER, ACTION_TIMER, ACTION_PREV, ACTION_NEXT)

        fun requestCode(action: String, widgetId: Int): Int = widgetId * ACTIONS.size + ACTIONS.indexOf(action)
    }
}
