package com.cansin.app.domain

import com.cansin.app.data.BeerEvent

data class TimerState(val beerId: Long? = null, val sessionId: Long? = null, val startedAt: Long? = null) {
    val running: Boolean get() = beerId != null && startedAt != null
}

enum class WidgetPage { BEER, TAB, WATER }

object TimerRules {
    fun canStart(enabled: Boolean, state: TimerState, myLatestBeer: BeerEvent?): Boolean =
        enabled && !state.running && myLatestBeer != null && myLatestBeer.finishedAt == null

    fun isCurrent(state: TimerState, sessionId: Long?): Boolean = state.running && state.sessionId == sessionId

    fun page(index: Int): WidgetPage = WidgetPage.entries[Math.floorMod(index, WidgetPage.entries.size)]

    fun step(page: WidgetPage, delta: Int): WidgetPage = page(page.ordinal + delta)
}
