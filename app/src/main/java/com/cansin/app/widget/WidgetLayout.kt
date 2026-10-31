package com.cansin.app.widget

object WidgetLayout {
    const val WIDE_MIN_WIDTH_DP = 250

    fun isWide(minWidthDp: Int): Boolean = minWidthDp >= WIDE_MIN_WIDTH_DP

    fun chronometerBase(nowWall: Long, nowElapsed: Long, lastBeerAt: Long): Long =
        nowElapsed - (nowWall - lastBeerAt).coerceAtLeast(0)
}
