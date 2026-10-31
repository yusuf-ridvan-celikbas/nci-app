package com.cansin.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.cansin.app.MainActivity
import com.cansin.app.R
import com.cansin.app.data.CounterSnapshot
import com.cansin.app.prefs.Settings
import com.cansin.app.prefs.ThemeMode
import com.cansin.app.system.AppLocale
import com.cansin.app.ui.theme.colorScheme

object WidgetRenderer {
    private class Colors(val surface: Int, val onSurface: Int, val muted: Int, val primary: Int, val onPrimary: Int)

    fun render(app: Context, settings: Settings, snapshot: CounterSnapshot, options: Bundle): RemoteViews {
        val context = AppLocale.localized(app, settings.language)
        val small = build(context, settings, snapshot, wide = false)
        val wide = build(context, settings, snapshot, wide = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(mapOf(SizeF(110f, 110f) to small, SizeF(WidgetLayout.WIDE_MIN_WIDTH_DP.toFloat(), 110f) to wide))
        }
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        return if (WidgetLayout.isWide(minWidth)) wide else small
    }

    private fun build(context: Context, settings: Settings, snapshot: CounterSnapshot, wide: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, if (wide) R.layout.widget_wide else R.layout.widget_small)
        views.setTextViewText(R.id.widget_count, snapshot.count.toString())
        views.setTextViewText(R.id.widget_add_label, context.getString(R.string.plus_one))
        views.setContentDescription(R.id.widget_add, context.getString(R.string.add_beer))
        views.setOnClickPendingIntent(R.id.widget_add, addIntent(context))
        views.setOnClickPendingIntent(R.id.widget_count, openAppIntent(context))
        if (wide) {
            views.setTextViewText(R.id.widget_title, context.getString(settings.appName.label))
            views.setTextViewText(R.id.widget_since_label, context.getString(R.string.widget_since_last))
            val last = snapshot.lastBeerAt
            if (last == null) {
                views.setViewVisibility(R.id.widget_since_row, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_since_row, View.VISIBLE)
                val base = WidgetLayout.chronometerBase(System.currentTimeMillis(), SystemClock.elapsedRealtime(), last)
                views.setChronometer(R.id.widget_since, base, null, true)
            }
        }
        applyColors(context, settings, views, wide)
        return views
    }

    private fun applyColors(context: Context, settings: Settings, views: RemoteViews, wide: Boolean) {
        val light = colors(context, settings, dark = false)
        val dark = colors(context, settings, dark = true)
        val followSystem = settings.themeMode == ThemeMode.SYSTEM
        val nightNow = when (settings.themeMode) {
            ThemeMode.SYSTEM -> isNight(context)
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        fun set(id: Int, method: String, pick: (Colors) -> Int) {
            if (followSystem && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorInt(id, method, pick(light), pick(dark))
            } else {
                views.setInt(id, method, pick(if (nightNow) dark else light))
            }
        }
        set(R.id.widget_bg, "setColorFilter") { it.surface }
        set(R.id.widget_count, "setTextColor") { it.onSurface }
        set(R.id.widget_add_bg, "setColorFilter") { it.primary }
        set(R.id.widget_add_label, "setTextColor") { it.onPrimary }
        if (wide) {
            set(R.id.widget_title, "setTextColor") { it.muted }
            set(R.id.widget_since_label, "setTextColor") { it.muted }
            set(R.id.widget_since, "setTextColor") { it.onSurface }
        }
    }

    private fun colors(context: Context, settings: Settings, dark: Boolean): Colors {
        val s = colorScheme(context, settings.accent, dark)
        return Colors(
            surface = s.surfaceContainer.toArgb(),
            onSurface = s.onSurface.toArgb(),
            muted = s.onSurfaceVariant.toArgb(),
            primary = s.primary.toArgb(),
            onPrimary = s.onPrimary.toArgb(),
        )
    }

    private fun isNight(context: Context) =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun addIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WidgetActionReceiver::class.java).setAction(WidgetActionReceiver.ACTION_ADD),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
