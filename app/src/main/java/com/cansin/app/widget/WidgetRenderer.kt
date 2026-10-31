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
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.cansin.app.MainActivity
import com.cansin.app.R
import com.cansin.app.data.CounterSnapshot
import com.cansin.app.domain.TimerRules
import com.cansin.app.domain.TimerState
import com.cansin.app.domain.WidgetPage
import com.cansin.app.prefs.Settings
import com.cansin.app.prefs.ThemeMode
import com.cansin.app.system.AppLocale
import com.cansin.app.ui.theme.colorScheme
import java.text.DecimalFormatSymbols

data class WidgetData(val settings: Settings, val snapshot: CounterSnapshot, val timer: TimerState)

object WidgetRenderer {
    private class Colors(val surface: Int, val onSurface: Int, val muted: Int, val primary: Int, val onPrimary: Int)

    private val dots = intArrayOf(R.id.widget_dot0, R.id.widget_dot1, R.id.widget_dot2)

    fun render(app: Context, data: WidgetData, page: WidgetPage, widgetId: Int, options: Bundle): RemoteViews {
        val context = AppLocale.localized(app, data.settings.language)
        val small = build(context, data, page, widgetId, wide = false)
        val wide = build(context, data, page, widgetId, wide = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(mapOf(SizeF(110f, 110f) to small, SizeF(WidgetLayout.WIDE_MIN_WIDTH_DP.toFloat(), 110f) to wide))
        }
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        return if (WidgetLayout.isWide(minWidth)) wide else small
    }

    private fun build(context: Context, data: WidgetData, page: WidgetPage, widgetId: Int, wide: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, if (wide) R.layout.widget_wide else R.layout.widget_small)
        val snapshot = data.snapshot
        val label = context.getString(
            when (page) {
                WidgetPage.BEER -> R.string.widget_page_beer
                WidgetPage.TAB -> R.string.widget_page_tab
                WidgetPage.WATER -> R.string.widget_page_water
            },
        )
        val pages = WidgetPage.entries.size
        views.setTextViewText(R.id.widget_page_label, label)
        views.setContentDescription(
            R.id.widget_page_label,
            context.getString(R.string.widget_page_position, label, page.ordinal + 1, pages),
        )
        views.setContentDescription(R.id.widget_prev, context.getString(R.string.widget_prev))
        views.setContentDescription(R.id.widget_next, context.getString(R.string.widget_next))
        views.setOnClickPendingIntent(R.id.widget_prev, action(context, WidgetActionReceiver.ACTION_PREV, widgetId))
        views.setOnClickPendingIntent(R.id.widget_next, action(context, WidgetActionReceiver.ACTION_NEXT, widgetId))
        views.setOnClickPendingIntent(R.id.widget_value, openApp(context, tab = page == WidgetPage.TAB))
        views.setTextViewTextSize(R.id.widget_add_label, TypedValue.COMPLEX_UNIT_SP, if (wide) 20f else 16f)

        val canAdd = snapshot.sessionId != null && snapshot.hasMe
        when (page) {
            WidgetPage.BEER -> {
                if (snapshot.versus) {
                    views.setTextViewText(R.id.widget_value, context.getString(R.string.versus_score, snapshot.red, snapshot.blue))
                    views.setContentDescription(R.id.widget_value, context.getString(R.string.versus_score_description, snapshot.red, snapshot.blue))
                    button(views, context.getString(R.string.plus_one), context.getString(R.string.versus_open), openApp(context, tab = false))
                } else {
                    views.setTextViewText(R.id.widget_value, snapshot.count.toString())
                    views.setContentDescription(R.id.widget_value, null)
                    button(views, context.getString(R.string.plus_one), context.getString(R.string.add_beer), action(context, WidgetActionReceiver.ACTION_ADD, widgetId))
                }
            }
            WidgetPage.TAB -> {
                val locale = context.resources.configuration.locales[0]
                val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
                views.setTextViewText(R.id.widget_value, data.settings.currency.format(snapshot.tabMinor, separator, locale.language == "tr"))
                views.setContentDescription(R.id.widget_value, null)
                val open = context.getString(R.string.widget_open)
                button(views, open, context.getString(R.string.widget_open_tab), openApp(context, tab = true))
            }
            WidgetPage.WATER -> {
                views.setTextViewText(R.id.widget_value, snapshot.water.toString())
                views.setContentDescription(R.id.widget_value, null)
                val intent = if (canAdd) action(context, WidgetActionReceiver.ACTION_WATER, widgetId) else openApp(context, tab = false)
                button(views, context.getString(R.string.plus_one), context.getString(R.string.water_add), intent)
            }
        }
        if (wide) {
            val sub = when (page) {
                WidgetPage.TAB -> context.resources.getQuantityString(R.plurals.notes_count, snapshot.notes, snapshot.notes)
                else -> ""
            }
            views.setTextViewText(R.id.widget_sub, sub)
            views.setViewVisibility(R.id.widget_sub, if (sub.isEmpty()) View.GONE else View.VISIBLE)
            timerRow(context, views, data, page, widgetId)
        }
        applyColors(context, data.settings, views, page, wide)
        return views
    }

    private fun button(views: RemoteViews, label: String, description: String, intent: PendingIntent) {
        views.setTextViewText(R.id.widget_add_label, label)
        views.setContentDescription(R.id.widget_add, description)
        views.setOnClickPendingIntent(R.id.widget_add, intent)
    }

    private fun timerRow(context: Context, views: RemoteViews, data: WidgetData, page: WidgetPage, widgetId: Int) {
        val snapshot = data.snapshot
        val running = TimerRules.isCurrent(data.timer, snapshot.sessionId)
        val canStart = TimerRules.canStart(data.settings.timerEnabled, data.timer, snapshot.myLatestBeer)
        val show = page == WidgetPage.BEER && data.settings.timerEnabled && (running || canStart)
        views.setViewVisibility(R.id.widget_timer_row, if (show) View.VISIBLE else View.GONE)
        if (!show) return
        views.setOnClickPendingIntent(R.id.widget_timer_toggle, action(context, WidgetActionReceiver.ACTION_TIMER, widgetId))
        views.setImageViewResource(R.id.widget_timer_toggle, if (running) R.drawable.ic_stop else R.drawable.ic_play)
        views.setContentDescription(R.id.widget_timer_toggle, context.getString(if (running) R.string.timer_stop else R.string.timer_start))
        views.setTextViewText(R.id.widget_timer_label, context.getString(if (running) R.string.timer_running else R.string.timer_idle))
        val startedAt = data.timer.startedAt
        if (running && startedAt != null) {
            views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
            val base = WidgetLayout.chronometerBase(System.currentTimeMillis(), SystemClock.elapsedRealtime(), startedAt)
            views.setChronometer(R.id.widget_timer, base, null, true)
        } else {
            views.setViewVisibility(R.id.widget_timer, View.GONE)
            views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime(), null, false)
        }
    }

    private fun applyColors(context: Context, settings: Settings, views: RemoteViews, page: WidgetPage, wide: Boolean) {
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
        set(R.id.widget_value, "setTextColor") { it.onSurface }
        set(R.id.widget_page_label, "setTextColor") { it.muted }
        set(R.id.widget_prev, "setColorFilter") { it.muted }
        set(R.id.widget_next, "setColorFilter") { it.muted }
        set(R.id.widget_add_bg, "setColorFilter") { it.primary }
        set(R.id.widget_add_label, "setTextColor") { it.onPrimary }
        dots.forEachIndexed { i, id ->
            set(id, "setColorFilter") { if (i == page.ordinal) it.onSurface else it.muted }
            views.setInt(id, "setImageAlpha", if (i == page.ordinal) 255 else 110)
        }
        if (wide) {
            set(R.id.widget_sub, "setTextColor") { it.muted }
            set(R.id.widget_timer_toggle, "setColorFilter") { it.primary }
            set(R.id.widget_timer_label, "setTextColor") { it.muted }
            set(R.id.widget_timer, "setTextColor") { it.onSurface }
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

    private fun action(context: Context, action: String, widgetId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        WidgetActionReceiver.requestCode(action, widgetId),
        Intent(context, WidgetActionReceiver::class.java)
            .setAction(action)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(context: Context, tab: Boolean): PendingIntent = PendingIntent.getActivity(
        context,
        if (tab) 1 else 0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, tab),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
