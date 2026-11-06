package com.cansin.app.widget

/** P31: one look per widget size. */
enum class WidgetSize { TINY, SMALL, STRIP, BIG }

object WidgetLayout {
    const val SMALL_MIN_DP = 110
    const val STRIP_MIN_WIDTH_DP = 180
    const val STRIP_MAX_HEIGHT_DP = 100
    const val BIG_MIN_WIDTH_DP = 250

    /** Launcher size in dp → look. 1×1 = TINY, 4×1 = STRIP, 4×2+ = BIG, else SMALL (2×2 pages). */
    fun sizeClass(widthDp: Int, heightDp: Int): WidgetSize = when {
        widthDp < SMALL_MIN_DP -> WidgetSize.TINY
        heightDp < STRIP_MAX_HEIGHT_DP && widthDp >= STRIP_MIN_WIDTH_DP -> WidgetSize.STRIP
        heightDp < SMALL_MIN_DP - 30 -> WidgetSize.TINY
        widthDp >= BIG_MIN_WIDTH_DP -> WidgetSize.BIG
        else -> WidgetSize.SMALL
    }

    fun chronometerBase(nowWall: Long, nowElapsed: Long, lastBeerAt: Long): Long =
        nowElapsed - (nowWall - lastBeerAt).coerceAtLeast(0)
}
