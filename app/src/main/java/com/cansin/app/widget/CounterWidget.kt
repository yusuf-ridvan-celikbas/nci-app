package com.cansin.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import com.cansin.app.CansinApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CounterWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = runAsync(context) { refreshAll(it) }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        runAsync(context) { refreshAll(it) }

    override fun onDeleted(context: Context, ids: IntArray) = runAsync(context) { it.settingsStore.removeWidgetPages(ids) }

    private fun runAsync(context: Context, block: suspend (CansinApp) -> Unit) {
        val app = context.applicationContext as CansinApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                block(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        suspend fun refreshAll(app: CansinApp) {
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, CounterWidget::class.java))
            if (ids.isEmpty()) return
            val data = WidgetData(app.settingsStore.settings.first(), app.sessionRepository.snapshot(), app.settingsStore.timerState.first())
            for (id in ids) {
                val config = WidgetConfig(app.settingsStore.widgetFocus(id), app.settingsStore.widgetPage(id))
                manager.updateAppWidget(id, WidgetRenderer.render(app, data, config, id, manager.getAppWidgetOptions(id)))
            }
        }
    }
}
