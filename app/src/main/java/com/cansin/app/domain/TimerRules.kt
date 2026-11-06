package com.cansin.app.domain

import com.cansin.app.data.BeerEvent

data class TimerState(val beerId: Long? = null, val sessionId: Long? = null, val startedAt: Long? = null) {
    val running: Boolean get() = beerId != null && startedAt != null
}

/** Widget pages; which ones a widget shows comes from [com.cansin.app.widget.WidgetContent.pages]. */
enum class WidgetPage { BEER, TAB, WATER, PROMILLE }

object TimerRules {
    fun canStart(enabled: Boolean, state: TimerState, myLatestBeer: BeerEvent?): Boolean =
        enabled && !state.running && myLatestBeer != null && myLatestBeer.finishedAt == null

    fun isCurrent(state: TimerState, sessionId: Long?): Boolean = state.running && state.sessionId == sessionId


}
