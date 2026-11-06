package com.cansin.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.cansin.app.MainActivity
import com.cansin.app.R
import com.cansin.app.data.CounterSnapshot
import com.cansin.app.data.DrinkKind
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side
import com.cansin.app.domain.BeerRules
import com.cansin.app.domain.Drink
import com.cansin.app.domain.Drinks
import com.cansin.app.domain.PromilleModel
import com.cansin.app.domain.TimerRules
import com.cansin.app.domain.TimerState
import com.cansin.app.domain.WidgetPage
import com.cansin.app.prefs.Settings
import com.cansin.app.prefs.ThemeMode
import com.cansin.app.prefs.hideCounts
import com.cansin.app.system.AppLocale
import com.cansin.app.ui.theme.VersusColors
import com.cansin.app.ui.theme.colorScheme
import java.text.DecimalFormatSymbols
import java.util.Date

data class WidgetData(val settings: Settings, val snapshot: CounterSnapshot, val timer: TimerState, val now: Long = System.currentTimeMillis())

/** Per widget: its setting (Auto / one thing) and the SMALL page index. */
data class WidgetConfig(val focus: WidgetFocus = WidgetFocus.AUTO, val pageIndex: Int = 0)

/**
 * P31: one look per size. API 31+ hands the launcher all four (it picks the best fit when resized);
 * API 26–30 picks one from the size options. Colours follow the app theme (light/dark pair on 31+).
 */
object WidgetRenderer {
    private class Colors(
        val surface: Int,
        val onSurface: Int,
        val muted: Int,
        val primary: Int,
        val onPrimary: Int,
        val container: Int,
    )

    /** Everything one build needs. */
    private class Ctx(val context: Context, val data: WidgetData, val widgetId: Int, val focus: WidgetFocus, val promille: Boolean) {
        val s get() = data.snapshot
        val hidden get() = data.settings.hideCounts
        val canWater get() = s.sessionId != null && s.hasMe
        /** +1 straight from the widget: solo, or my side in Together. Versus uses the red/blue buttons. */
        val canAdd get() = !s.versus && (!s.group || s.hasMe)
    }

    // Narrow pages look, by height: 0 = 2×2, 1 = 2×3, 2 = 2×4+.
    private val NARROW_MINUS_DP = floatArrayOf(32f, 44f, 56f)
    private val NARROW_ADD_DP = floatArrayOf(36f, 52f, 64f)
    private val NARROW_MINUS_SP = floatArrayOf(13f, 16f, 19f)
    private val NARROW_ADD_SP = floatArrayOf(13f, 18f, 22f)
    private val NARROW_VALUE_SP = floatArrayOf(24f, 34f, 44f)

    private val dots = intArrayOf(R.id.widget_dot0, R.id.widget_dot1, R.id.widget_dot2, R.id.widget_dot3)

    /** View ids of one BIG count row (direct R.id refs so resource shrinking keeps them). */
    private class Row(
        val label: Int, val sub: Int,
        val minus: Int, val minusBg: Int, val minusLabel: Int,
        val plus: Int, val plusBg: Int, val plusLabel: Int,
    )

    private val ROW_DRINKS = Row(
        R.id.big_drinks_label, R.id.big_drinks_sub, R.id.big_drinks_minus, R.id.big_drinks_minus_bg, R.id.big_drinks_minus_label,
        R.id.big_drinks_plus, R.id.big_drinks_plus_bg, R.id.big_drinks_plus_label,
    )
    private val ROW_RED = Row(
        R.id.big_red_label, R.id.big_red_sub, R.id.big_red_minus, R.id.big_red_minus_bg, R.id.big_red_minus_label,
        R.id.big_red_plus, R.id.big_red_plus_bg, R.id.big_red_plus_label,
    )
    private val ROW_BLUE = Row(
        R.id.big_blue_label, R.id.big_blue_sub, R.id.big_blue_minus, R.id.big_blue_minus_bg, R.id.big_blue_minus_label,
        R.id.big_blue_plus, R.id.big_blue_plus_bg, R.id.big_blue_plus_label,
    )
    private const val NO_VIEW = 0

    private val XL_RED = Row(
        R.id.xl_red_label, NO_VIEW, R.id.xl_red_minus, R.id.xl_red_minus_bg, R.id.xl_red_minus_label,
        R.id.xl_red_plus, R.id.xl_red_plus_bg, R.id.xl_red_plus_label,
    )
    private val XL_BLUE = Row(
        R.id.xl_blue_label, NO_VIEW, R.id.xl_blue_minus, R.id.xl_blue_minus_bg, R.id.xl_blue_minus_label,
        R.id.xl_blue_plus, R.id.xl_blue_plus_bg, R.id.xl_blue_plus_label,
    )
    private val XL_WATER = Row(
        R.id.xl_water_label, NO_VIEW, R.id.xl_water_minus, R.id.xl_water_minus_bg, R.id.xl_water_minus_label,
        R.id.xl_water_plus, R.id.xl_water_plus_bg, R.id.xl_water_plus_label,
    )
    private val XL_TAB_LABELS = intArrayOf(
        R.id.xl_tab_label1, R.id.xl_tab_label2, R.id.xl_tab_label3, R.id.xl_tab_label4,
        R.id.xl_tab_label5, R.id.xl_tab_label6, R.id.xl_tab_label7, R.id.xl_tab_label8,
    )
    private val XL_TAB_AMOUNTS = intArrayOf(
        R.id.xl_tab_amount1, R.id.xl_tab_amount2, R.id.xl_tab_amount3, R.id.xl_tab_amount4,
        R.id.xl_tab_amount5, R.id.xl_tab_amount6, R.id.xl_tab_amount7, R.id.xl_tab_amount8,
    )

    private val ROW_WATER = Row(
        R.id.big_water_label, R.id.big_water_sub, R.id.big_water_minus, R.id.big_water_minus_bg, R.id.big_water_minus_label,
        R.id.big_water_plus, R.id.big_water_plus_bg, R.id.big_water_plus_label,
    )

    fun render(app: Context, data: WidgetData, config: WidgetConfig, widgetId: Int, options: Bundle): RemoteViews {
        val context = AppLocale.localized(app, data.settings.language)
        val promille = WidgetContent.promilleOn(data.settings.widgetPromille, data.settings.profile != null)
        val c = Ctx(context, data, widgetId, WidgetContent.focus(config.focus, promille), promille)
        val pages = WidgetContent.pages(c.focus, promille)
        val page = WidgetContent.page(pages, config.pageIndex)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(
                mapOf(
                    SizeF(40f, 40f) to tiny(c),
                    SizeF(40f, WidgetLayout.TWO_ROW_MIN_HEIGHT_DP.toFloat()) to small(c, pages, page, narrow = true, scale = 0),
                    SizeF(40f, WidgetLayout.COLUMN_MIN_HEIGHT_DP.toFloat()) to small(c, pages, page, narrow = true, scale = 1),
                    SizeF(40f, WidgetLayout.XL_MIN_HEIGHT_DP.toFloat()) to small(c, pages, page, narrow = true, scale = 2),
                    SizeF(WidgetLayout.STRIP_MIN_WIDTH_DP.toFloat(), WidgetLayout.TWO_ROW_MIN_HEIGHT_DP.toFloat()) to small(c, pages, page),
                    SizeF(WidgetLayout.STRIP_MIN_WIDTH_DP.toFloat(), 40f) to pager(c, pages, page, compact = false),
                    SizeF(WidgetLayout.BIG_MIN_WIDTH_DP.toFloat(), WidgetLayout.COLUMN_MIN_HEIGHT_DP.toFloat()) to big(c),
                    SizeF(WidgetLayout.BIG_MIN_WIDTH_DP.toFloat(), WidgetLayout.XL_MIN_HEIGHT_DP.toFloat()) to xl(c),
                ),
            )
        }
        val size = WidgetLayout.sizeClass(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
        )
        return when (size) {
            WidgetSize.TINY -> tiny(c)
            WidgetSize.TALL -> small(c, pages, page, narrow = true, scale = WidgetLayout.narrowScale(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)))
            WidgetSize.SMALL -> small(c, pages, page)
            WidgetSize.STRIP -> pager(c, pages, page, compact = false)
            WidgetSize.BIG -> big(c)
            WidgetSize.XL -> xl(c)
        }
    }

    // ---------- values ----------

    private fun value(c: Ctx, page: WidgetPage): String = when (page) {
        WidgetPage.BEER -> if (c.s.versus) c.context.getString(R.string.versus_score, c.s.red, c.s.blue) else c.s.count.toString()
        WidgetPage.TAB -> money(c, c.s.tabMinor)
        WidgetPage.WATER -> c.s.water.toString()
        WidgetPage.PROMILLE -> promille(c)?.let { c.context.getString(R.string.widget_promille_value, it) } ?: "–"
    }

    private fun money(c: Ctx, minor: Long): String {
        val locale = c.context.resources.configuration.locales[0]
        return c.data.settings.currency.format(minor, DecimalFormatSymbols.getInstance(locale).decimalSeparator, locale.language == "tr")
    }

    /** Current ‰ estimate from my drinks; null when off, no profile or no drinks. */
    private fun promille(c: Ctx): String? {
        val profile = c.data.settings.profile ?: return null
        if (!c.promille || c.s.myBeers.isEmpty()) return null
        val estimate = PromilleModel.estimate(profile, c.s.myBeers.map { Drink(it.at, it.finishedAt, it.sizeMl, it.abv) }, c.data.now)
        return String.format(c.context.resources.configuration.locales[0], "%.2f", estimate.current)
    }

    private fun time(c: Ctx, millis: Long): String = DateFormat.getTimeFormat(c.context).format(Date(millis))

    /** "+1: Efes · 50 cl" — what the widget's +1 adds. */
    private fun addsLabel(c: Ctx): String {
        val d = Drinks.default(c.data.settings.defaultDrink, c.data.settings.beerSize, c.data.settings.defaultBrand, c.data.settings.defaultServing)
        val sep = DecimalFormatSymbols.getInstance(c.context.resources.configuration.locales[0]).decimalSeparator
        val name = listOfNotNull(d.brand ?: c.context.getString(drinkName(d.kind)), d.serving?.let { c.context.getString(if (it == com.cansin.app.data.Serving.DRAFT) R.string.serving_draft else R.string.serving_bottle) }).joinToString(" · ")
        return c.context.getString(R.string.widget_adds, name, BeerRules.formatCl(d.size.sizeMl, sep))
    }

    private fun drinkName(kind: DrinkKind): Int = when (kind) {
        DrinkKind.BEER -> R.string.drink_beer
        DrinkKind.WINE -> R.string.drink_wine
        DrinkKind.RAKI -> R.string.drink_raki
        DrinkKind.SHOT -> R.string.drink_shot
        DrinkKind.COCKTAIL -> R.string.drink_cocktail
        DrinkKind.CUSTOM -> R.string.drink_custom
    }

    private fun sideName(c: Ctx, side: Side): String = when (side) {
        Side.RED -> c.s.redName ?: c.context.getString(R.string.side_red)
        else -> c.s.blueName ?: c.context.getString(R.string.side_blue)
    }

    // ---------- TINY (1×1) ----------

    /** One number + its main action (TINY / TALL). */
    private class Single(val value: String, val label: String, val button: String, val intent: PendingIntent, val description: String)

    private fun single(c: Ctx): Single {
        val context = c.context
        if (c.hidden) {
            return Single(
                context.getString(R.string.widget_hidden), "", context.getString(R.string.widget_open),
                openApp(context, false), context.getString(R.string.widget_open_app),
            )
        }
        val page = WidgetContent.tiny(c.focus)
        val value = value(c, page)
        return when (page) {
            WidgetPage.BEER -> if (c.canAdd) {
                Single(value, context.getString(R.string.widget_page_beer), context.getString(R.string.plus_one), action(c, WidgetActionReceiver.ACTION_ADD), context.getString(R.string.add_beer))
            } else {
                Single(value, context.getString(R.string.widget_page_beer), context.getString(R.string.widget_open), openApp(context, false), context.getString(R.string.widget_open_app))
            }
            WidgetPage.WATER -> if (c.canWater) {
                Single(value, context.getString(R.string.widget_page_water), context.getString(R.string.plus_one), action(c, WidgetActionReceiver.ACTION_WATER), context.getString(R.string.water_add))
            } else {
                Single(value, context.getString(R.string.widget_page_water), context.getString(R.string.widget_open), openApp(context, false), context.getString(R.string.widget_open_app))
            }
            WidgetPage.TAB -> Single(value, context.getString(R.string.widget_page_tab), context.getString(R.string.widget_open), openApp(context, true), context.getString(R.string.widget_open_tab))
            WidgetPage.PROMILLE -> Single(value, context.getString(R.string.widget_promille_short), context.getString(R.string.widget_open), openApp(context, false), context.getString(R.string.widget_open_app))
        }
    }

    // ---------- TINY (1×1): the count inside a round +1 ----------

    private fun tiny(c: Ctx): RemoteViews {
        val views = RemoteViews(c.context.packageName, R.layout.widget_tiny)
        val one = single(c)
        views.setOnClickPendingIntent(R.id.widget_bg, openApp(c.context, tab = false))
        views.setTextViewText(R.id.widget_value, one.value)
        views.setTextViewText(R.id.tiny_hint, one.button)
        views.setContentDescription(R.id.tiny_tap, listOf(one.label, one.value, one.description).filter { it.isNotEmpty() }.joinToString(", "))
        views.setOnClickPendingIntent(R.id.tiny_tap, one.intent)
        tint(c, views) { set ->
            set(R.id.widget_bg, "setColorFilter") { it.surface }
            set(R.id.tiny_circle, "setColorFilter") { it.primary }
            set(R.id.widget_value, "setTextColor") { it.onPrimary }
            set(R.id.tiny_hint, "setTextColor") { it.onPrimary }
        }
        return views
    }

    // ---------- 1-row widgets: ‹ label •∘∘ value (−1) (+1) › ----------

    private val pagerDots = intArrayOf(R.id.pager_dot0, R.id.pager_dot1, R.id.pager_dot2, R.id.pager_dot3)

    /** [compact] (2×1): no label and no −1, just ‹ dots value (+1) ›. */
    private fun pager(c: Ctx, pages: List<WidgetPage>, page: WidgetPage, compact: Boolean): RemoteViews {
        val context = c.context
        val views = RemoteViews(context.packageName, R.layout.widget_pager)
        val label = context.getString(pageLabel(page))
        val paged = pages.size > 1
        views.setOnClickPendingIntent(R.id.widget_bg, openApp(context, tab = false))
        views.setOnClickPendingIntent(R.id.pager_text, openApp(context, tab = page == WidgetPage.TAB))
        views.setTextViewText(R.id.pager_label, label)
        views.setViewVisibility(R.id.pager_label, if (compact) View.GONE else View.VISIBLE)
        views.setContentDescription(R.id.pager_text, context.getString(R.string.widget_page_position, label, pages.indexOf(page) + 1, pages.size))
        views.setViewVisibility(R.id.pager_prev, if (paged) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.pager_next, if (paged) View.VISIBLE else View.GONE)
        views.setContentDescription(R.id.pager_prev, context.getString(R.string.widget_prev))
        views.setContentDescription(R.id.pager_next, context.getString(R.string.widget_next))
        views.setOnClickPendingIntent(R.id.pager_prev, action(c, WidgetActionReceiver.ACTION_PREV))
        views.setOnClickPendingIntent(R.id.pager_next, action(c, WidgetActionReceiver.ACTION_NEXT))
        pagerDots.forEachIndexed { i, id -> views.setViewVisibility(id, if (paged && i < pages.size) View.VISIBLE else View.GONE) }
        views.setTextViewText(R.id.pager_value, if (c.hidden) context.getString(R.string.widget_hidden) else value(c, page))

        // Buttons: (label, intent, description) for −1 and +1; null hides it.
        val plusLabel = context.getString(R.string.plus_one)
        val open = Triple(context.getString(R.string.widget_open), openApp(context, page == WidgetPage.TAB), context.getString(R.string.widget_open_app))
        var versus = false
        val (minus, plus) = when {
            c.hidden -> null to open.copy(third = context.getString(R.string.widget_open_app))
            page == WidgetPage.BEER && c.s.versus -> {
                versus = true
                Triple(plusLabel, action(c, WidgetActionReceiver.ACTION_ADD_RED), context.getString(R.string.versus_add, sideName(c, Side.RED))) to
                    Triple(plusLabel, action(c, WidgetActionReceiver.ACTION_ADD_BLUE), context.getString(R.string.versus_add, sideName(c, Side.BLUE)))
            }
            page == WidgetPage.BEER && c.canAdd ->
                Triple(context.getString(R.string.minus_one), action(c, WidgetActionReceiver.ACTION_REMOVE), context.getString(R.string.remove_beer)) to
                    Triple(plusLabel, action(c, WidgetActionReceiver.ACTION_ADD), context.getString(R.string.add_beer))
            page == WidgetPage.WATER && c.canWater ->
                Triple(context.getString(R.string.minus_one), action(c, WidgetActionReceiver.ACTION_WATER_REMOVE), context.getString(R.string.water_remove)) to
                    Triple(plusLabel, action(c, WidgetActionReceiver.ACTION_WATER), context.getString(R.string.water_add))
            else -> null to open
        }
        val showMinus = minus != null && (!compact || versus)
        views.setViewVisibility(R.id.pager_minus, if (showMinus) View.VISIBLE else View.GONE)
        if (minus != null && showMinus) {
            views.setTextViewText(R.id.pager_minus_label, minus.first)
            views.setOnClickPendingIntent(R.id.pager_minus, minus.second)
            views.setContentDescription(R.id.pager_minus, minus.third)
        }
        views.setTextViewText(R.id.pager_plus_label, plus.first)
        views.setTextViewTextSize(R.id.pager_plus_label, TypedValue.COMPLEX_UNIT_SP, if (plus.first.length > 2) 11f else 15f)
        views.setOnClickPendingIntent(R.id.pager_plus, plus.second)
        views.setContentDescription(R.id.pager_plus, plus.third)

        val current = pages.indexOf(page)
        tint(c, views) { set ->
            set(R.id.widget_bg, "setColorFilter") { it.surface }
            set(R.id.pager_prev, "setColorFilter") { it.muted }
            set(R.id.pager_next, "setColorFilter") { it.muted }
            set(R.id.pager_label, "setTextColor") { it.muted }
            set(R.id.pager_value, "setTextColor") { it.onSurface }
            pagerDots.forEachIndexed { i, id -> set(id, "setColorFilter") { if (i == current) it.onSurface else it.muted } }
            if (!versus) {
                set(R.id.pager_minus_bg, "setColorFilter") { it.container }
                set(R.id.pager_minus_label, "setTextColor") { it.onSurface }
                set(R.id.pager_plus_bg, "setColorFilter") { it.primary }
                set(R.id.pager_plus_label, "setTextColor") { it.onPrimary }
            }
        }
        pagerDots.forEachIndexed { i, id -> views.setInt(id, "setImageAlpha", if (i == current) 255 else 110) }
        if (versus) {
            versusTint(views, R.id.pager_minus_bg, R.id.pager_minus_label, Side.RED)
            versusTint(views, R.id.pager_plus_bg, R.id.pager_plus_label, Side.BLUE)
        }
        return views
    }

    // ---------- SMALL (2×2): pages ----------

    /** 2-row widgets: the pages look; [narrow] = 1×2 / skinny version (same ids). */
    private fun small(c: Ctx, pages: List<WidgetPage>, page: WidgetPage, narrow: Boolean = false, scale: Int = 0): RemoteViews {
        val context = c.context
        val views = RemoteViews(context.packageName, if (narrow) R.layout.widget_small_narrow else R.layout.widget_small)
        views.setOnClickPendingIntent(R.id.widget_bg, openApp(context, tab = false))
        val label = context.getString(pageLabel(page))
        val paged = pages.size > 1
        views.setTextViewText(R.id.widget_page_label, label)
        views.setContentDescription(R.id.widget_page_label, context.getString(R.string.widget_page_position, label, pages.indexOf(page) + 1, pages.size))
        views.setViewVisibility(R.id.widget_prev, if (paged) View.VISIBLE else View.INVISIBLE)
        views.setViewVisibility(R.id.widget_next, if (paged) View.VISIBLE else View.INVISIBLE)
        dots.forEachIndexed { i, id -> views.setViewVisibility(id, if (paged && i < pages.size) View.VISIBLE else View.GONE) }
        views.setContentDescription(R.id.widget_prev, context.getString(R.string.widget_prev))
        views.setContentDescription(R.id.widget_next, context.getString(R.string.widget_next))
        views.setOnClickPendingIntent(R.id.widget_prev, action(c, WidgetActionReceiver.ACTION_PREV))
        views.setOnClickPendingIntent(R.id.widget_next, action(c, WidgetActionReceiver.ACTION_NEXT))
        views.setOnClickPendingIntent(R.id.widget_value, openApp(context, tab = page == WidgetPage.TAB))
        views.setTextViewTextSize(R.id.widget_add_label, TypedValue.COMPLEX_UNIT_SP, if (narrow) NARROW_ADD_SP[scale] else 16f)
        if (narrow) {
            // Taller narrow widgets (2×3, 2×5…) get bigger buttons and number.
            views.setTextViewTextSize(R.id.widget_value, TypedValue.COMPLEX_UNIT_SP, NARROW_VALUE_SP[scale])
            views.setTextViewTextSize(R.id.widget_minus_label, TypedValue.COMPLEX_UNIT_SP, NARROW_MINUS_SP[scale])
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setViewLayoutWidth(R.id.widget_minus, NARROW_MINUS_DP[scale], TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_minus, NARROW_MINUS_DP[scale], TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutWidth(R.id.widget_add, NARROW_ADD_DP[scale], TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutHeight(R.id.widget_add, NARROW_ADD_DP[scale], TypedValue.COMPLEX_UNIT_DIP)
            }
        }
        views.setTextViewText(R.id.widget_value, value(c, page))
        views.setContentDescription(R.id.widget_value, null)
        when (page) {
            WidgetPage.BEER -> when {
                c.s.versus -> {
                    views.setContentDescription(R.id.widget_value, context.getString(R.string.versus_score_description, c.s.red, c.s.blue))
                    button(views, context.getString(R.string.plus_one), context.getString(R.string.versus_open), openApp(context, false))
                }
                c.canAdd -> button(views, context.getString(R.string.plus_one), context.getString(R.string.add_beer), action(c, WidgetActionReceiver.ACTION_ADD))
                else -> button(views, context.getString(R.string.plus_one), context.getString(R.string.group_open), openApp(context, false))
            }
            WidgetPage.TAB -> button(views, context.getString(R.string.widget_open), context.getString(R.string.widget_open_tab), openApp(context, true))
            WidgetPage.WATER -> {
                val intent = if (c.canWater) action(c, WidgetActionReceiver.ACTION_WATER) else openApp(context, false)
                button(views, context.getString(R.string.plus_one), context.getString(R.string.water_add), intent)
            }
            WidgetPage.PROMILLE -> button(views, context.getString(R.string.widget_open), context.getString(R.string.widget_open_app), openApp(context, false))
        }
        if (c.hidden) {
            views.setTextViewText(R.id.widget_value, context.getString(R.string.widget_hidden))
            views.setContentDescription(R.id.widget_value, context.getString(R.string.widget_hidden_description))
            button(views, context.getString(R.string.widget_open), context.getString(R.string.widget_open_app), openApp(context, false))
        }
        val minus = when {
            c.hidden -> null
            page == WidgetPage.BEER && c.canAdd -> action(c, WidgetActionReceiver.ACTION_REMOVE) to context.getString(R.string.remove_beer)
            page == WidgetPage.WATER && c.canWater -> action(c, WidgetActionReceiver.ACTION_WATER_REMOVE) to context.getString(R.string.water_remove)
            else -> null
        }
        views.setViewVisibility(R.id.widget_minus, if (minus == null) View.GONE else View.VISIBLE)
        minus?.let { (intent, description) ->
            views.setOnClickPendingIntent(R.id.widget_minus, intent)
            views.setContentDescription(R.id.widget_minus, description)
        }
        views.setOnClickPendingIntent(R.id.widget_bg, openApp(context, tab = false))
        val current = pages.indexOf(page)
        tint(c, views) { set ->
            set(R.id.widget_bg, "setColorFilter") { it.surface }
            set(R.id.widget_value, "setTextColor") { it.onSurface }
            set(R.id.widget_page_label, "setTextColor") { it.muted }
            set(R.id.widget_prev, "setColorFilter") { it.muted }
            set(R.id.widget_next, "setColorFilter") { it.muted }
            set(R.id.widget_add_bg, "setColorFilter") { it.primary }
            set(R.id.widget_add_label, "setTextColor") { it.onPrimary }
            set(R.id.widget_minus_bg, "setColorFilter") { it.container }
            set(R.id.widget_minus_label, "setTextColor") { it.onSurface }
            dots.forEachIndexed { i, id -> set(id, "setColorFilter") { if (i == current) it.onSurface else it.muted } }
        }
        dots.forEachIndexed { i, id -> views.setInt(id, "setImageAlpha", if (i == current) 255 else 110) }
        return views
    }

    private fun pageLabel(page: WidgetPage) = when (page) {
        WidgetPage.BEER -> R.string.widget_page_beer
        WidgetPage.TAB -> R.string.widget_page_tab
        WidgetPage.WATER -> R.string.widget_page_water
        WidgetPage.PROMILLE -> R.string.widget_page_promille
    }

    private fun button(views: RemoteViews, label: String, description: String, intent: PendingIntent) {
        views.setTextViewText(R.id.widget_add_label, label)
        views.setContentDescription(R.id.widget_add, description)
        views.setOnClickPendingIntent(R.id.widget_add, intent)
    }

    // ---------- BIG (4×2+): dashboard ----------

    private fun big(c: Ctx): RemoteViews {
        val context = c.context
        val views = RemoteViews(context.packageName, R.layout.widget_big)
        views.setOnClickPendingIntent(R.id.widget_bg, openApp(context, tab = false))
        val s = c.s
        views.setTextViewText(R.id.big_title, context.getString(R.string.name_salgam))
        views.setTextViewText(
            R.id.big_since,
            s.lastBeerAt?.let { context.getString(R.string.widget_last_drink, time(c, it)) } ?: context.getString(R.string.nav_tonight),
        )
        val blocks = WidgetContent.blocks(WidgetSize.BIG, c.focus, s.mode, c.promille, c.hidden)
        views.setViewVisibility(R.id.big_hidden, if (c.hidden) View.VISIBLE else View.GONE)
        if (c.hidden) {
            views.setTextViewText(R.id.big_hidden, context.getString(R.string.widget_hidden))
            views.setContentDescription(R.id.big_hidden, context.getString(R.string.widget_hidden_description))
            views.setOnClickPendingIntent(R.id.big_hidden, openApp(context, false))
        }
        fun show(id: Int, on: Boolean) = views.setViewVisibility(id, if (on) View.VISIBLE else View.GONE)
        show(R.id.big_drinks_row, WidgetBlock.DRINKS in blocks)
        show(R.id.big_red_row, WidgetBlock.VERSUS in blocks)
        show(R.id.big_blue_row, WidgetBlock.VERSUS in blocks)
        show(R.id.big_water_row, WidgetBlock.WATER in blocks)
        show(R.id.big_tab_row, WidgetBlock.TAB in blocks)
        show(R.id.big_promille_row, WidgetBlock.PROMILLE in blocks)

        if (WidgetBlock.DRINKS in blocks) {
            val sub = listOfNotNull(
                addsLabel(c).takeIf { c.canAdd },
                context.getString(R.string.widget_people, s.seatCount).takeIf { s.mode == SessionMode.GROUP },
            ).joinToString(" · ")
            countRow(
                c, views, ROW_DRINKS, context.getString(R.string.detail_beers, s.count), sub,
                minus = if (c.canAdd) action(c, WidgetActionReceiver.ACTION_REMOVE) else null,
                plus = if (c.canAdd) action(c, WidgetActionReceiver.ACTION_ADD) else openApp(context, false),
                what = context.getString(R.string.widget_page_beer),
            )
        }
        if (WidgetBlock.VERSUS in blocks) {
            listOf(Triple(Side.RED, s.red, ROW_RED), Triple(Side.BLUE, s.blue, ROW_BLUE)).forEach { (side, n, row) ->
                val name = sideName(c, side)
                val me = if (s.meSide == side) context.getString(R.string.versus_name_me, name) else name
                countRow(
                    c, views, row, context.getString(R.string.widget_side_count, me, n), "",
                    minus = if (s.meSide == side) action(c, WidgetActionReceiver.ACTION_REMOVE) else null,
                    plus = action(c, if (side == Side.RED) WidgetActionReceiver.ACTION_ADD_RED else WidgetActionReceiver.ACTION_ADD_BLUE),
                    what = name,
                )
            }
        }
        if (WidgetBlock.WATER in blocks) {
            countRow(
                c, views, ROW_WATER, context.getString(R.string.water_count, s.water), "",
                minus = if (c.canWater) action(c, WidgetActionReceiver.ACTION_WATER_REMOVE) else null,
                plus = if (c.canWater) action(c, WidgetActionReceiver.ACTION_WATER) else openApp(context, false),
                what = context.getString(R.string.widget_page_water),
            )
        }
        if (WidgetBlock.TAB in blocks) {
            views.setTextViewText(R.id.big_tab_label, context.getString(R.string.tab_total, money(c, s.tabMinor)))
            val sep = DecimalFormatSymbols.getInstance(context.resources.configuration.locales[0]).decimalSeparator
            val lines = s.lines.joinToString(" · ") { line ->
                val name = line.brand ?: context.getString(drinkName(line.kind))
                context.getString(R.string.widget_tab_line, name, BeerRules.formatCl(line.sizeMl, sep), line.count)
            }
            views.setTextViewText(R.id.big_tab_sub, lines)
            views.setOnClickPendingIntent(R.id.big_tab_row, openApp(context, true))
        }
        if (WidgetBlock.PROMILLE in blocks) {
            views.setTextViewText(R.id.big_promille_label, value(c, WidgetPage.PROMILLE))
            views.setTextViewText(R.id.big_promille_sub, context.getString(R.string.widget_promille_note, time(c, c.data.now)))
            views.setOnClickPendingIntent(R.id.big_promille_row, openApp(context, false))
        }
        timerRow(c, views, show = WidgetBlock.TIMER in blocks)

        val rows = listOf(ROW_DRINKS, ROW_RED, ROW_BLUE, ROW_WATER)
        tint(c, views) { set ->
            set(R.id.widget_bg, "setColorFilter") { it.surface }
            set(R.id.big_title, "setTextColor") { it.onSurface }
            set(R.id.big_since, "setTextColor") { it.muted }
            set(R.id.big_hidden, "setTextColor") { it.onSurface }
            rows.forEach { row ->
                set(row.label, "setTextColor") { it.onSurface }
                set(row.sub, "setTextColor") { it.muted }
                set(row.minusBg, "setColorFilter") { it.container }
                set(row.minusLabel, "setTextColor") { it.onSurface }
                if (row !== ROW_RED && row !== ROW_BLUE) {
                    set(row.plusBg, "setColorFilter") { it.primary }
                    set(row.plusLabel, "setTextColor") { it.onPrimary }
                }
            }
            listOf(R.id.big_tab_label, R.id.big_promille_label).forEach { set(it, "setTextColor") { c2 -> c2.onSurface } }
            listOf(R.id.big_tab_sub, R.id.big_promille_sub).forEach { set(it, "setTextColor") { c2 -> c2.muted } }
            set(R.id.widget_timer_toggle, "setColorFilter") { it.primary }
            set(R.id.widget_timer_label, "setTextColor") { it.muted }
            set(R.id.widget_timer, "setTextColor") { it.onSurface }
        }
        versusTint(views, R.id.big_red_plus_bg, R.id.big_red_plus_label, Side.RED)
        versusTint(views, R.id.big_blue_plus_bg, R.id.big_blue_plus_label, Side.BLUE)
        return views
    }

    // ---------- XL (4×4 and up): big counter + Tab list ----------

    private fun xl(c: Ctx): RemoteViews {
        val context = c.context
        val views = RemoteViews(context.packageName, R.layout.widget_xl)
        val s = c.s
        views.setOnClickPendingIntent(R.id.widget_bg, openApp(context, tab = false))
        views.setTextViewText(R.id.xl_title, context.getString(R.string.name_salgam))
        views.setTextViewText(
            R.id.xl_since,
            s.lastBeerAt?.let { context.getString(R.string.widget_last_drink, time(c, it)) } ?: context.getString(R.string.nav_tonight),
        )
        val blocks = WidgetContent.blocks(WidgetSize.XL, c.focus, s.mode, c.promille, c.hidden)
        fun show(id: Int, on: Boolean) = views.setViewVisibility(id, if (on) View.VISIBLE else View.GONE)
        show(R.id.xl_hidden, c.hidden)
        if (c.hidden) {
            views.setTextViewText(R.id.xl_hidden, context.getString(R.string.widget_hidden))
            views.setContentDescription(R.id.xl_hidden, context.getString(R.string.widget_hidden_description))
            views.setOnClickPendingIntent(R.id.xl_hidden, openApp(context, false))
        }
        show(R.id.xl_hero, WidgetBlock.HERO in blocks)
        show(R.id.xl_red_row, WidgetBlock.VERSUS in blocks)
        show(R.id.xl_blue_row, WidgetBlock.VERSUS in blocks)
        show(R.id.xl_water_row, WidgetBlock.WATER in blocks)
        show(R.id.xl_promille_row, WidgetBlock.PROMILLE in blocks)
        show(R.id.xl_tab, WidgetBlock.TAB_LIST in blocks)

        if (WidgetBlock.HERO in blocks) {
            views.setTextViewText(R.id.xl_count, s.count.toString())
            views.setOnClickPendingIntent(R.id.xl_hero_text, openApp(context, false))
            views.setTextViewText(
                R.id.xl_count_label,
                listOfNotNull(
                    context.getString(R.string.widget_page_beer),
                    context.getString(R.string.widget_people, s.seatCount).takeIf { s.mode == SessionMode.GROUP },
                ).joinToString(" · "),
            )
            show(R.id.xl_minus, c.canAdd)
            views.setOnClickPendingIntent(R.id.xl_minus, action(c, WidgetActionReceiver.ACTION_REMOVE))
            views.setContentDescription(R.id.xl_minus, context.getString(R.string.remove_beer))
            views.setOnClickPendingIntent(R.id.xl_plus, if (c.canAdd) action(c, WidgetActionReceiver.ACTION_ADD) else openApp(context, false))
            views.setContentDescription(R.id.xl_plus, context.getString(if (c.canAdd) R.string.add_beer else R.string.group_open))
            views.setTextViewText(R.id.xl_adds, if (c.canAdd) addsLabel(c) else "")
        }
        if (WidgetBlock.VERSUS in blocks) {
            listOf(Triple(Side.RED, s.red, XL_RED), Triple(Side.BLUE, s.blue, XL_BLUE)).forEach { (side, n, row) ->
                val name = sideName(c, side)
                val me = if (s.meSide == side) context.getString(R.string.versus_name_me, name) else name
                countRow(
                    c, views, row, context.getString(R.string.widget_side_count, me, n), "",
                    minus = if (s.meSide == side) action(c, WidgetActionReceiver.ACTION_REMOVE) else null,
                    plus = action(c, if (side == Side.RED) WidgetActionReceiver.ACTION_ADD_RED else WidgetActionReceiver.ACTION_ADD_BLUE),
                    what = name,
                )
            }
        }
        if (WidgetBlock.WATER in blocks) {
            countRow(
                c, views, XL_WATER, context.getString(R.string.water_count, s.water), "",
                minus = if (c.canWater) action(c, WidgetActionReceiver.ACTION_WATER_REMOVE) else null,
                plus = if (c.canWater) action(c, WidgetActionReceiver.ACTION_WATER) else openApp(context, false),
                what = context.getString(R.string.widget_page_water),
            )
        }
        if (WidgetBlock.PROMILLE in blocks) {
            views.setTextViewText(R.id.xl_promille_label, value(c, WidgetPage.PROMILLE))
            views.setTextViewText(R.id.xl_promille_sub, context.getString(R.string.widget_promille_note, time(c, c.data.now)))
            views.setOnClickPendingIntent(R.id.xl_promille_row, openApp(context, false))
        }
        timerRow(c, views, show = WidgetBlock.TIMER in blocks)
        if (WidgetBlock.TAB_LIST in blocks) tabList(c, views)

        tint(c, views) { set ->
            set(R.id.widget_bg, "setColorFilter") { it.surface }
            set(R.id.xl_title, "setTextColor") { it.onSurface }
            set(R.id.xl_since, "setTextColor") { it.muted }
            set(R.id.xl_hidden, "setTextColor") { it.onSurface }
            set(R.id.xl_count, "setTextColor") { it.onSurface }
            set(R.id.xl_count_label, "setTextColor") { it.muted }
            set(R.id.xl_adds, "setTextColor") { it.muted }
            set(R.id.xl_minus_bg, "setColorFilter") { it.container }
            set(R.id.xl_minus_label, "setTextColor") { it.onSurface }
            set(R.id.xl_plus_bg, "setColorFilter") { it.primary }
            set(R.id.xl_plus_label, "setTextColor") { it.onPrimary }
            listOf(XL_RED, XL_BLUE, XL_WATER).forEach { row ->
                set(row.label, "setTextColor") { it.onSurface }
                set(row.minusBg, "setColorFilter") { it.container }
                set(row.minusLabel, "setTextColor") { it.onSurface }
            }
            set(XL_WATER.plusBg, "setColorFilter") { it.primary }
            set(XL_WATER.plusLabel, "setTextColor") { it.onPrimary }
            set(R.id.xl_promille_label, "setTextColor") { it.onSurface }
            set(R.id.xl_promille_sub, "setTextColor") { it.muted }
            set(R.id.xl_tab_title, "setTextColor") { it.onSurface }
            set(R.id.xl_tab_total, "setTextColor") { it.onSurface }
            set(R.id.xl_tab_more, "setTextColor") { it.primary }
            XL_TAB_LABELS.forEach { id -> set(id, "setTextColor") { it.onSurface } }
            XL_TAB_AMOUNTS.forEach { id -> set(id, "setTextColor") { it.muted } }
            set(R.id.widget_timer_toggle, "setColorFilter") { it.primary }
            set(R.id.widget_timer_label, "setTextColor") { it.muted }
            set(R.id.widget_timer, "setTextColor") { it.onSurface }
        }
        versusTint(views, R.id.xl_red_plus_bg, R.id.xl_red_plus_label, Side.RED)
        versusTint(views, R.id.xl_blue_plus_bg, R.id.xl_blue_plus_label, Side.BLUE)
        return views
    }

    /** Up to 8 rows: drink lines, then the newest notes; "+N more · open Tab" for the rest. Tap = open the Tab. */
    private fun tabList(c: Ctx, views: RemoteViews) {
        val context = c.context
        val s = c.s
        val sep = DecimalFormatSymbols.getInstance(context.resources.configuration.locales[0]).decimalSeparator
        views.setOnClickPendingIntent(R.id.xl_tab, openApp(context, tab = true))
        views.setTextViewText(R.id.xl_tab_title, context.getString(R.string.widget_page_tab))
        views.setTextViewText(R.id.xl_tab_total, money(c, s.tabMinor))
        val rows = WidgetContent.tabRows(s.allLines.size, s.recentNotes.size)
        val items = rows.lines.map { i ->
            val line = s.allLines[i]
            val name = line.brand ?: context.getString(drinkName(line.kind))
            context.getString(R.string.widget_tab_line, name, BeerRules.formatCl(line.sizeMl, sep), line.count) to
                (if (line.unitMinor == null) "–" else money(c, line.totalMinor))
        } + rows.notes.map { i ->
            val note = s.recentNotes[i]
            context.getString(R.string.widget_tab_note, time(c, note.editedTime ?: note.createdAt), note.text) to
                (note.amountMinor?.let { money(c, it) } ?: "")
        }
        XL_TAB_LABELS.indices.forEach { i ->
            val item = items.getOrNull(i)
            views.setViewVisibility(XL_TAB_LABELS[i], if (item == null) View.GONE else View.VISIBLE)
            views.setViewVisibility(XL_TAB_AMOUNTS[i], if (item == null) View.GONE else View.VISIBLE)
            if (item != null) {
                views.setTextViewText(XL_TAB_LABELS[i], item.first)
                views.setTextViewText(XL_TAB_AMOUNTS[i], item.second)
            }
        }
        val more = when {
            items.isEmpty() -> context.getString(R.string.widget_tab_empty)
            rows.overflow > 0 -> context.resources.getQuantityString(R.plurals.widget_tab_more, rows.overflow, rows.overflow)
            else -> ""
        }
        views.setTextViewText(R.id.xl_tab_more, more)
        views.setViewVisibility(R.id.xl_tab_more, if (more.isEmpty()) View.GONE else View.VISIBLE)
    }

    /** "Label · −1 · +1" row; a null [minus] hides −1. */
    private fun countRow(c: Ctx, views: RemoteViews, row: Row, label: String, sub: String, minus: PendingIntent?, plus: PendingIntent, what: String) {
        val context = c.context
        views.setTextViewText(row.label, label)
        if (row.sub != NO_VIEW) {
            views.setTextViewText(row.sub, sub)
            views.setViewVisibility(row.sub, if (sub.isEmpty()) View.GONE else View.VISIBLE)
        }
        views.setViewVisibility(row.minus, if (minus == null) View.GONE else View.VISIBLE)
        minus?.let {
            views.setOnClickPendingIntent(row.minus, it)
            views.setContentDescription(row.minus, context.getString(R.string.widget_remove_one, what))
        }
        views.setOnClickPendingIntent(row.plus, plus)
        views.setContentDescription(row.plus, context.getString(R.string.widget_add_one, what))
    }

    private fun timerRow(c: Ctx, views: RemoteViews, show: Boolean) {
        val context = c.context
        val running = TimerRules.isCurrent(c.data.timer, c.s.sessionId)
        val canStart = TimerRules.canStart(c.data.settings.timerEnabled, c.data.timer, c.s.myLatestBeer)
        val visible = show && c.data.settings.timerEnabled && (running || canStart)
        views.setViewVisibility(R.id.widget_timer_row, if (visible) View.VISIBLE else View.GONE)
        if (!visible) return
        views.setOnClickPendingIntent(R.id.widget_timer_toggle, action(c, WidgetActionReceiver.ACTION_TIMER))
        views.setImageViewResource(R.id.widget_timer_toggle, if (running) R.drawable.ic_stop else R.drawable.ic_play)
        views.setContentDescription(R.id.widget_timer_toggle, context.getString(if (running) R.string.timer_stop else R.string.timer_start))
        views.setTextViewText(R.id.widget_timer_label, context.getString(if (running) R.string.timer_running else R.string.timer_idle))
        val startedAt = c.data.timer.startedAt
        if (running && startedAt != null) {
            views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
            views.setChronometer(R.id.widget_timer, WidgetLayout.chronometerBase(System.currentTimeMillis(), SystemClock.elapsedRealtime(), startedAt), null, true)
        } else {
            views.setViewVisibility(R.id.widget_timer, View.GONE)
            views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime(), null, false)
        }
    }

    // ---------- colours ----------

    /** Fixed Versus RED/BLUE (same in every theme, like the app). */
    private fun versusTint(views: RemoteViews, bg: Int, label: Int, side: Side) {
        val (fill, text) = if (side == Side.RED) VersusColors.red to VersusColors.onRed else VersusColors.blue to VersusColors.onBlue
        views.setInt(bg, "setColorFilter", fill.toArgb())
        views.setTextColor(label, text.toArgb())
    }

    private fun tint(c: Ctx, views: RemoteViews, block: (set: (Int, String, (Colors) -> Int) -> Unit) -> Unit) {
        val settings = c.data.settings
        val light = colors(c.context, settings, dark = false)
        val dark = colors(c.context, settings, dark = true)
        val followSystem = settings.themeMode == ThemeMode.SYSTEM
        val nightNow = when (settings.themeMode) {
            ThemeMode.SYSTEM -> isNight(c.context)
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        block { id, method, pick ->
            if (followSystem && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorInt(id, method, pick(light), pick(dark))
            } else {
                views.setInt(id, method, pick(if (nightNow) dark else light))
            }
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
            container = s.surfaceContainerHighest.toArgb(),
        )
    }

    private fun isNight(context: Context) =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    // ---------- intents ----------

    private fun action(c: Ctx, action: String): PendingIntent = PendingIntent.getBroadcast(
        c.context,
        WidgetActionReceiver.requestCode(action, c.widgetId),
        Intent(c.context, WidgetActionReceiver::class.java)
            .setAction(action)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, c.widgetId),
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
