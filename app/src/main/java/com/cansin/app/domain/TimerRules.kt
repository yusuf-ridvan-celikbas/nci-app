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

    /** P32 "start with +1": what to do when my drink [beer] was just added. */
    data class AutoStart(val finishPrevious: Long?, val next: TimerState)

    /** Null = leave the timer alone (setting off, timer off, or it already runs for this drink). */
    fun autoStart(enabled: Boolean, auto: Boolean, state: TimerState, beer: BeerEvent, now: Long): AutoStart? {
        if (!enabled || !auto || state.beerId == beer.id) return null
        val previous = state.beerId?.takeIf { state.running && state.sessionId == beer.sessionId }
        return AutoStart(previous, TimerState(beer.id, beer.sessionId, now))
    }

    /** The timed drink was removed (undo / −1): stop showing a timer for it. */
    fun clearsFor(state: TimerState, removedBeerId: Long): Boolean = state.beerId == removedBeerId


}
