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
                    SizeF(110f, 110f) to small(c, pages, page),
                    SizeF(WidgetLayout.STRIP_MIN_WIDTH_DP.toFloat(), 40f) to strip(c),
                    SizeF(WidgetLayout.BIG_MIN_WIDTH_DP.toFloat(), 110f) to big(c),
                ),
            )
        }
        val size = WidgetLayout.sizeClass(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
        )
        return when (size) {
            WidgetSize.TINY -> tiny(c)
            WidgetSize.SMALL -> small(c, pages, page)
            WidgetSize.STRIP -> strip(c)
            WidgetSize.BIG -> big(c)
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
        val d = Drinks.default(c.data.settings.defaultDrink, c.data.settings.beerSize, c.data.settings.defaultBrand)
        val sep = DecimalFormatSymbols.getInstance(c.context.resources.configuration.locales[0]).decimalSeparator
        val name = d.brand ?: c.context.getString(drinkName(d.kind))
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

    private fun tiny(c: Ctx): RemoteViews {
        val views = RemoteViews(c.context.packageName, R.layout.widget_tiny)
        val page = WidgetContent.tiny(c.focus)
        if (c.hidden) {
            views.setTextViewText(R.id.widget_value, c.context.getString(R.string.widget_hidden))
            views.setTextViewText(R.id.tiny_hint, c.context.getString(R.string.widget_open))
            views.setContentDescription(R.id.tiny_tap, c.context.getString(R.string.widget_open_app))
            views.setOnClickPendingIntent(R.id.tiny_tap, openApp(c.context, tab = false))
        } else {
            views.setTextViewText(R.id.widget_value, value(c, page))
            val (hint, intent, description) = when (page) {
                WidgetPage.BEER -> if (c.canAdd) {
                    Triple(c.context.getString(R.string.plus_one), action(c, WidgetActionReceiver.ACTION_ADD), c.context.getString(R.string.add_beer))
                } else {
                    Triple(c.context.getString(R.string.widget_open), openApp(c.context, false), c.context.getString(R.string.widget_open_app))
                }
                WidgetPage.WATER -> if (c.canWater) {
                    Triple(c.context.getString(R.string.widget_water_plus), action(c, WidgetActionReceiver.ACTION_WATER), c.context.getString(R.string.water_add))
                } else {
                    Triple(c.context.getString(R.string.widget_page_water), openApp(c.context, false), c.context.getString(R.string.widget_open_app))
                }
                WidgetPage.TAB -> Triple(c.context.getString(R.string.widget_page_tab), openApp(c.context, true), c.context.getString(R.string.widget_open_tab))
                WidgetPage.PROMILLE -> Triple(c.context.getString(R.string.widget_promille_short), openApp(c.context, false), c.context.getString(R.string.widget_open_app))
            }
            views.setTextViewText(R.id.tiny_hint, hint)
            views.setContentDescription(R.id.tiny_tap, value(c, page) + ", " + description)
            views.setOnClickPendingIntent(R.id.tiny_tap, intent)
        }
        tint(c, views) { set ->
            set(R.id.widget_bg, "setColorFilter") { it.surface }
            set(R.id.widget_value, "setTextColor") { it.onSurface }
            set(R.id.tiny_hint, "setTextColor") { it.primary }
        }
        return views
    }

    // ---------- SMALL (2×2): pages ----------

    private fun small(c: Ctx, pages: List<WidgetPage>, page: WidgetPage): RemoteViews {
        val context = c.context
        val views = RemoteViews(context.packageName, R.layout.widget_small)
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
        views.setTextViewTextSize(R.id.widget_add_label, TypedValue.COMPLEX_UNIT_SP, 16f)
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
        val current = pages.indexOf(page)
        tint(c, views) { set ->
            set(R.id.widget_bg, "setColorFilter") { it.surface }
            set(R.id.widget_value, "setTextColor") { it.onSurface }
            set(R.id.widget_page_label, "setTextColor") { it.muted }
            set(R.id.widget_prev, "setColorFilter") { it.muted }
            set(R.id.widget_next, "setColorFilter") { it.muted }
            set(R.id.widget_add_bg, "setColorFilter") { it.primary }
            set(R.id.widget_add_label, "setTextColor") { it.onPrimary }
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

    // ---------- STRIP (4×1): up to three cells ----------

    private class StripIds(val cell: Int, val label: Int, val value: Int, val btn: Int, val btnBg: Int, val btnLabel: Int, val text: Int)

    private val STRIP_CELLS = listOf(
        StripIds(R.id.strip_cell1, R.id.strip_label1, R.id.strip_value1, R.id.strip_btn1, R.id.strip_btn1_bg, R.id.strip_btn1_label, R.id.strip_text1),
        StripIds(R.id.strip_cell2, R.id.strip_label2, R.id.strip_value2, R.id.strip_btn2, R.id.strip_btn2_bg, R.id.strip_btn2_label, R.id.strip_text2),
        StripIds(R.id.strip_cell3, R.id.strip_label3, R.id.strip_value3, R.id.strip_btn3, R.id.strip_btn3_bg, R.id.strip_btn3_label, R.id.strip_text3),
    )

    private class Cell(val label: String, val value: String, val button: Triple<String, PendingIntent, String>?, val tap: PendingIntent, val side: Side? = null)

    private fun strip(c: Ctx): RemoteViews {
        val context = c.context
        val views = RemoteViews(context.packageName, R.layout.widget_strip)
        val cells = if (c.hidden) {
            listOf(Cell(context.getString(R.string.widget_open_app), context.getString(R.string.widget_hidden), null, openApp(context, false)))
        } else {
            WidgetContent.blocks(WidgetSize.STRIP, c.focus, c.s.mode, c.promille, false).flatMap { block -> stripCells(c, block) }.take(3)
        }
        val ids = STRIP_CELLS
        ids.forEachIndexed { i, ids1 ->
            val cell = ids1.cell; val label = ids1.label; val value = ids1.value; val btn = ids1.btn; val btnLabel = ids1.btnLabel; val text = ids1.text
            val data = cells.getOrNull(i)
            views.setViewVisibility(cell, if (data == null) View.GONE else View.VISIBLE)
            if (data == null) return@forEachIndexed
            views.setTextViewText(label, data.label)
            views.setTextViewText(value, data.value)
            views.setOnClickPendingIntent(text, data.tap)
            views.setViewVisibility(btn, if (data.button == null) View.GONE else View.VISIBLE)
            data.button?.let { (l, intent, description) ->
                views.setTextViewText(btnLabel, l)
                views.setContentDescription(btn, description)
                views.setOnClickPendingIntent(btn, intent)
            }
        }
        tint(c, views) { set ->
            set(R.id.widget_bg, "setColorFilter") { it.surface }
            ids.forEachIndexed { i, cell ->
                set(cell.label, "setTextColor") { it.muted }
                set(cell.value, "setTextColor") { it.onSurface }
                if (cells.getOrNull(i)?.side == null) {
                    set(cell.btnBg, "setColorFilter") { it.primary }
                    set(cell.btnLabel, "setTextColor") { it.onPrimary }
                }
            }
        }
        cells.forEachIndexed { i, cell -> cell.side?.let { side -> versusTint(views, ids[i].btnBg, ids[i].btnLabel, side) } }
        return views
    }

    private fun stripCells(c: Ctx, block: WidgetBlock): List<Cell> {
        val context = c.context
        return when (block) {
            WidgetBlock.DRINKS -> listOf(
                Cell(
                    context.getString(R.string.widget_page_beer), c.s.count.toString(),
                    if (c.canAdd) Triple(context.getString(R.string.plus_one), action(c, WidgetActionReceiver.ACTION_ADD), context.getString(R.string.add_beer)) else null,
                    openApp(context, false),
                ),
            )
            WidgetBlock.VERSUS -> listOf(Side.RED to c.s.red, Side.BLUE to c.s.blue).map { (side, n) ->
                val name = sideName(c, side)
                Cell(
                    name, n.toString(),
                    Triple(context.getString(R.string.plus_one), action(c, if (side == Side.RED) WidgetActionReceiver.ACTION_ADD_RED else WidgetActionReceiver.ACTION_ADD_BLUE), context.getString(R.string.versus_add, name)),
                    openApp(context, false), side,
                )
            }
            WidgetBlock.WATER -> listOf(
                Cell(
                    context.getString(R.string.widget_page_water), c.s.water.toString(),
                    if (c.canWater) Triple(context.getString(R.string.plus_one), action(c, WidgetActionReceiver.ACTION_WATER), context.getString(R.string.water_add)) else null,
                    openApp(context, false),
                ),
            )
            WidgetBlock.TAB -> listOf(Cell(context.getString(R.string.widget_page_tab), money(c, c.s.tabMinor), null, openApp(context, true)))
            WidgetBlock.PROMILLE -> listOf(Cell(context.getString(R.string.widget_promille_short), value(c, WidgetPage.PROMILLE), null, openApp(context, false)))
            WidgetBlock.TIMER -> emptyList()
        }
    }

    // ---------- BIG (4×2+): dashboard ----------

    private fun big(c: Ctx): RemoteViews {
        val context = c.context
        val views = RemoteViews(context.packageName, R.layout.widget_big)
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

    /** "Label · −1 · +1" row; a null [minus] hides −1. */
    private fun countRow(c: Ctx, views: RemoteViews, row: Row, label: String, sub: String, minus: PendingIntent?, plus: PendingIntent, what: String) {
        val context = c.context
        views.setTextViewText(row.label, label)
        views.setTextViewText(row.sub, sub)
        views.setViewVisibility(row.sub, if (sub.isEmpty()) View.GONE else View.VISIBLE)
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
