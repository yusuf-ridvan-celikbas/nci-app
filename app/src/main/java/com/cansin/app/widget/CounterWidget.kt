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
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshAsync(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        refreshAsync(context)

    private fun refreshAsync(context: Context) {
        val app = context.applicationContext as CansinApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                refreshAll(app)
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
            val settings = app.settingsStore.settings.first()
            val snapshot = app.sessionRepository.snapshot()
            for (id in ids) {
                manager.updateAppWidget(id, WidgetRenderer.render(app, settings, snapshot, manager.getAppWidgetOptions(id)))
            }
        }
    }
}
