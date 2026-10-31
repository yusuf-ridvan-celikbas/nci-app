package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.Session

data class SessionStats(val sessionId: Long, val startedAt: Long, val beers: Int, val peak: Double?, val paceMin: Double?)

data class Overview(
    val sessions: Int = 0,
    val avgBeers: Double? = null,
    val avgPaceMin: Double? = null,
    val waterPerBeer: Double? = null,
    val perSession: List<SessionStats> = emptyList(),
)

object StatsRules {
    const val CHART_SESSIONS = 20

    fun durationMs(session: Session): Long? = session.endedAt?.let { (it - session.startedAt).coerceAtLeast(0) }

    fun paceMin(times: List<Long>): Double? {
        if (times.size < 2) return null
        return (times.max() - times.min()) / 60_000.0 / (times.size - 1)
    }

    fun peak(profile: BodyProfile?, beers: List<BeerEvent>): Double? {
        if (profile == null || beers.isEmpty()) return null
        val drinks = beers.map { Drink(it.at, it.finishedAt, it.sizeMl, it.abv) }
        return PromilleModel.estimate(profile, drinks, beers.maxOf { it.at }).peak
    }

    fun session(session: Session, beers: List<BeerEvent>, profile: BodyProfile?): SessionStats? {
        if (VersusRules.meSide(session) == null) return null
        val mine = beers.filter { it.sessionId == session.id && VersusRules.isMine(it, session) }
        return SessionStats(session.id, session.startedAt, mine.size, peak(profile, mine), paceMin(mine.map { it.at }))
    }

    fun overview(sessions: List<Session>, beers: List<BeerEvent>, waterBySession: Map<Long, Int>, profile: BodyProfile?): Overview {
        val bySession = beers.groupBy { it.sessionId }
        val stats = sessions.sortedBy { it.startedAt }.mapNotNull { session(it, bySession[it.id].orEmpty(), profile) }
        if (stats.isEmpty()) return Overview()
        val totalBeers = stats.sumOf { it.beers }
        val intervals = stats.sumOf { (it.beers - 1).coerceAtLeast(0) }
        val span = stats.sumOf { s -> s.paceMin?.let { it * (s.beers - 1) } ?: 0.0 }
        val water = stats.sumOf { waterBySession[it.sessionId] ?: 0 }
        return Overview(
            sessions = stats.size,
            avgBeers = totalBeers.toDouble() / stats.size,
            avgPaceMin = if (intervals > 0) span / intervals else null,
            waterPerBeer = if (totalBeers > 0) water.toDouble() / totalBeers else null,
            perSession = stats.takeLast(CHART_SESSIONS),
        )
    }
}
