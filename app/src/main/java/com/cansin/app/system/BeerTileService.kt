package com.cansin.app.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cansin.app.CansinApp
import com.cansin.app.MainActivity
import com.cansin.app.R
import com.cansin.app.domain.QuickAction
import com.cansin.app.prefs.hideCounts
import com.cansin.app.widget.CounterWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Quick Settings "+1 beer". Bound only by the system (BIND_QUICK_SETTINGS_TILE); runs after unlock. */
class BeerTileService : TileService() {
    private val app get() = application as CansinApp

    override fun onStartListening() {
        app.appScope.launch { render() }
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(::add) else add()
    }

    private fun add() {
        app.appScope.launch {
            val settings = app.settingsStore.settings.first()
            val snapshot = app.sessionRepository.snapshot()
            if (QuickAction.tileAddsDirectly(settings.ageConfirmed, snapshot.versus) && !settings.hideCounts) {
                app.sessionRepository.addBeer()
                CounterWidget.refreshAll(app)
                render()
            } else {
                openApp()
            }
        }
    }

    private suspend fun render() {
        val tile = qsTile ?: return
        val settings = app.settingsStore.settings.first()
        val snapshot = app.sessionRepository.snapshot()
        val context = AppLocale.localized(app, settings.language)
        val direct = QuickAction.tileAddsDirectly(settings.ageConfirmed, snapshot.versus) && !settings.hideCounts
        tile.label = context.getString(R.string.tile_label)
        tile.state = if (snapshot.sessionId != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (direct && !settings.hideCounts) snapshot.count.toString() else context.getString(R.string.tile_open_app)
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            collapseLegacy(intent)
        }
    }

    /** API 26–33 only (guarded above); the Intent overload throws only on 34+. */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun collapseLegacy(intent: Intent) = startActivityAndCollapse(intent)
}
