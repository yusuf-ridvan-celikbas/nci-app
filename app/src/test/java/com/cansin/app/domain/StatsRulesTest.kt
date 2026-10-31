package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsRulesTest {
    private val min = 60_000L
    private val profile = BodyProfile(Sex.MALE, 30, 180, 80, License.NONE)
    private val solo = Session(id = 1, startedAt = 0, endedAt = 120 * min)
    private val versus = Session(id = 2, startedAt = 1_000 * min, endedAt = 1_100 * min, mode = SessionMode.VERSUS, meSide = Side.RED)
    private val anonymous = versus.copy(id = 3, meSide = null)

    private fun beer(session: Long, minute: Long, side: Side = Side.SOLO) = BeerEvent(sessionId = session, side = side, at = minute * min)

    private val beers = listOf(
        beer(1, 0), beer(1, 30), beer(1, 60),
        beer(2, 1_000, Side.RED), beer(2, 1_020, Side.RED), beer(2, 1_005, Side.BLUE),
        beer(3, 1_000, Side.RED),
    )

    @Test
    fun paceIsMinutesBetweenBeers() {
        assertEquals(30.0, StatsRules.paceMin(listOf(0, 30 * min, 60 * min))!!, 1e-9)
        assertNull(StatsRules.paceMin(listOf(0)))
    }

    @Test
    fun durationOfEndedSession() {
        assertEquals(120 * min, StatsRules.durationMs(solo))
        assertNull(StatsRules.durationMs(solo.copy(endedAt = null)))
    }

    @Test
    fun versusCountsOnlyMySideAndSkipsSessionsWithoutMe() {
        val overview = StatsRules.overview(listOf(versus, solo, anonymous), beers, mapOf(1L to 1, 2L to 1, 3L to 9), profile)
        assertEquals(2, overview.sessions)
        assertEquals(listOf(1L, 2L), overview.perSession.map { it.sessionId })
        assertEquals(listOf(3, 2), overview.perSession.map { it.beers })
        assertEquals(2.5, overview.avgBeers!!, 1e-9)
        assertEquals((60.0 + 20.0) / 3, overview.avgPaceMin!!, 1e-9)
        assertEquals(2.0 / 5, overview.waterPerBeer!!, 1e-9)
    }

    @Test
    fun peakNeedsProfile() {
        val withProfile = StatsRules.session(solo, beers, profile)!!
        assertTrue(withProfile.peak!! > 0)
        assertNull(StatsRules.session(solo, beers, null)!!.peak)
    }

    @Test
    fun emptyHistory() {
        assertEquals(Overview(), StatsRules.overview(emptyList(), emptyList(), emptyMap(), profile))
        assertEquals(Overview(), StatsRules.overview(listOf(anonymous), beers, emptyMap(), profile))
    }

    @Test
    fun chartKeepsLatestSessions() {
        val many = (1..30L).map { Session(id = it, startedAt = it * 10_000 * min, endedAt = it * 10_000 * min + 1) }
        val overview = StatsRules.overview(many, many.map { beer(it.id, it.startedAt / min) }, emptyMap(), null)
        assertEquals(StatsRules.CHART_SESSIONS, overview.perSession.size)
        assertEquals(30L, overview.perSession.last().sessionId)
    }
}
