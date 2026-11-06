package com.cansin.app.widget

/** P31: one look per widget size. */
/** TINY 1×1/2×1 (round +1), TALL narrow pages (1–2 columns, 2+ rows), SMALL pages (3×2, 4×2), STRIP 1-row pages, BIG 4×3, XL 4×4+. */
enum class WidgetSize { TINY, TALL, SMALL, STRIP, BIG, XL }

object WidgetLayout {
    const val SMALL_MIN_DP = 110
    const val STRIP_MIN_WIDTH_DP = 180
    const val STRIP_MAX_HEIGHT_DP = 100
    const val TWO_ROW_MIN_HEIGHT_DP = 100
    const val BIG_MIN_WIDTH_DP = 250
    const val XL_MIN_HEIGHT_DP = 300
    const val COLUMN_MIN_HEIGHT_DP = 200

    /** Launcher size in dp → look. */
    fun sizeClass(widthDp: Int, heightDp: Int): WidgetSize = when {
        heightDp < STRIP_MAX_HEIGHT_DP && widthDp >= STRIP_MIN_WIDTH_DP -> WidgetSize.STRIP // 3×1, 4×1: 1-row pages
        heightDp < STRIP_MAX_HEIGHT_DP -> WidgetSize.TINY // 1×1, 2×1: round +1
        widthDp < STRIP_MIN_WIDTH_DP -> WidgetSize.TALL // 1×2, 2×2, 2×3…: narrow pages
        widthDp >= BIG_MIN_WIDTH_DP && heightDp >= XL_MIN_HEIGHT_DP -> WidgetSize.XL
        widthDp >= BIG_MIN_WIDTH_DP && heightDp >= COLUMN_MIN_HEIGHT_DP -> WidgetSize.BIG // 4×3
        else -> WidgetSize.SMALL // 3×2, 4×2, 3×3: pages
    }

    /** Narrow pages look: 0 = 2 rows, 1 = 3 rows, 2 = 4+ rows (bigger buttons as it grows). */
    fun narrowScale(heightDp: Int): Int = when {
        heightDp >= XL_MIN_HEIGHT_DP -> 2
        heightDp >= COLUMN_MIN_HEIGHT_DP -> 1
        else -> 0
    }

    fun chronometerBase(nowWall: Long, nowElapsed: Long, lastBeerAt: Long): Long =
        nowElapsed - (nowWall - lastBeerAt).coerceAtLeast(0)
}
