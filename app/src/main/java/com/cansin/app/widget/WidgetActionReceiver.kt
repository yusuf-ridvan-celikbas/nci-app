package com.cansin.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.cansin.app.CansinApp
import com.cansin.app.data.Side
import com.cansin.app.domain.TimerRules
import com.cansin.app.prefs.hideCounts
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
                val hidden = app.settingsStore.settings.first().hideCounts
                when (action) {
                    ACTION_PREV, ACTION_NEXT -> {
                        val settings = app.settingsStore.settings.first()
                        val promille = WidgetContent.promilleOn(settings.widgetPromille, settings.profile != null)
                        val pages = WidgetContent.pages(WidgetContent.focus(app.settingsStore.widgetFocus(widgetId), promille), promille)
                        val index = app.settingsStore.widgetPage(widgetId) + if (action == ACTION_NEXT) 1 else -1
                        app.settingsStore.setWidgetPage(widgetId, Math.floorMod(index, pages.size))
                    }
                    else -> if (!hidden) counting(app, action)
                }
                CounterWidget.refreshAll(app)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun counting(app: CansinApp, action: String) {
        when (action) {
            ACTION_ADD -> app.sessionRepository.addMyBeer()
            ACTION_REMOVE -> app.sessionRepository.removeMyLatest()
            ACTION_WATER -> app.sessionRepository.addWater()
            ACTION_WATER_REMOVE -> app.sessionRepository.removeMyLatestWater()
            ACTION_ADD_RED -> app.sessionRepository.addBeer(side = Side.RED)
            ACTION_ADD_BLUE -> app.sessionRepository.addBeer(side = Side.BLUE)
            ACTION_TIMER -> {
                val running = TimerRules.isCurrent(app.settingsStore.timerState.first(), app.sessionRepository.snapshot().sessionId)
                if (running) app.drinkTimer.stop() else app.drinkTimer.start()
            }
        }
    }

    companion object {
        const val ACTION_ADD = "com.cansin.app.widget.ADD"
        const val ACTION_WATER = "com.cansin.app.widget.WATER"
        const val ACTION_TIMER = "com.cansin.app.widget.TIMER"
        const val ACTION_PREV = "com.cansin.app.widget.PREV"
        const val ACTION_NEXT = "com.cansin.app.widget.NEXT"
        const val ACTION_REMOVE = "com.cansin.app.widget.REMOVE"
        const val ACTION_WATER_REMOVE = "com.cansin.app.widget.WATER_REMOVE"
        const val ACTION_ADD_RED = "com.cansin.app.widget.ADD_RED"
        const val ACTION_ADD_BLUE = "com.cansin.app.widget.ADD_BLUE"
        private val ACTIONS = listOf(
            ACTION_ADD, ACTION_WATER, ACTION_TIMER, ACTION_PREV, ACTION_NEXT,
            ACTION_REMOVE, ACTION_WATER_REMOVE, ACTION_ADD_RED, ACTION_ADD_BLUE,
        )

        fun requestCode(action: String, widgetId: Int): Int = widgetId * ACTIONS.size + ACTIONS.indexOf(action)
    }
}
