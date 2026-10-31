package com.cansin.app.data

import com.cansin.app.domain.VersusSetup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VersusRepositoryTest {
    private val sessions = FakeSessionDao()
    private val beers = FakeBeerDao()
    private val notes = FakeNoteDao()
    private var now = 1_000_000L
    private val log = mutableListOf<String>()
    private val repo = SessionRepository(
        sessions, beers, notes, clock = { now },
        listener = object : SessionListener {
            override suspend fun onBeerAdded(event: BeerEvent, mine: Boolean) { log += "${event.side}:$mine" }
            override suspend fun onSessionClosed(sessionId: Long) { log += "closed:$sessionId" }
        },
    )

    private suspend fun start(me: Side? = null) = repo.startVersus(VersusSetup("  Ali  ", "", meSide = me))

    @Test
    fun startEndsOpenSoloSession() = runBlocking {
        val solo = repo.add()
        val versus = start()
        assertNotNull(sessions.rows.value.single { it.id == solo.sessionId }.endedAt)
        assertEquals(versus.id, repo.openSession.first()?.id)
        assertEquals(SessionMode.VERSUS, versus.mode)
        assertEquals("Ali", versus.redName)
        assertNull(versus.blueName)
    }

    @Test
    fun startDeletesEmptySoloSession() = runBlocking {
        repo.undo(repo.add())
        repo.addNote("x", null).let { repo.deleteNote(it!!) }
        start()
        assertEquals(1, sessions.rows.value.size)
    }

    @Test
    fun sidesAreCountedSeparately() = runBlocking {
        start()
        repo.addBeer(side = Side.RED)
        repo.addBeer(side = Side.RED)
        repo.addBeer(side = Side.BLUE)
        val snapshot = repo.snapshot()
        assertEquals(true, snapshot.versus)
        assertEquals(2, snapshot.red)
        assertEquals(1, snapshot.blue)
        assertEquals(3, snapshot.count)
    }

    @Test
    fun soloBeerIsRejectedInVersusAndSideBeerOutsideVersus() = runBlocking {
        assertNull(repo.addBeer(side = Side.RED))
        assertEquals(0, sessions.rows.value.size)
        start()
        assertNull(repo.addBeer())
        assertEquals(0, beers.rows.value.size)
    }

    @Test
    fun removeLatestOnlyTouchesThatSide() = runBlocking {
        start()
        repo.addBeer(side = Side.RED)
        now += 1_000
        repo.addBeer(side = Side.BLUE)
        repo.removeLatest(Side.RED)
        assertEquals(listOf(Side.BLUE), beers.rows.value.map { it.side })
    }

    @Test
    fun emptyVersusSessionSurvivesUndo() = runBlocking {
        val versus = start()
        repo.undo(repo.addBeer(side = Side.BLUE)!!)
        assertEquals(versus.id, repo.openSession.first()?.id)
    }

    @Test
    fun mineFollowsMeSide() = runBlocking {
        start(me = Side.BLUE)
        repo.addBeer(side = Side.RED)
        repo.addBeer(side = Side.BLUE)
        assertEquals(listOf("RED:false", "BLUE:true"), log)
    }

    @Test
    fun addMyBeerUsesMeSideOrNothing() = runBlocking {
        start(me = Side.RED)
        assertEquals(Side.RED, repo.addMyBeer(finishPrevious = true)?.side)
        repo.updateVersus(VersusSetup(meSide = null))
        assertNull(repo.addMyBeer())
        assertEquals(Side.SOLO, SessionRepository(FakeSessionDao(), FakeBeerDao(), FakeNoteDao()).addMyBeer()?.side)
    }

    @Test
    fun updateVersusRenamesWithoutNewSession() = runBlocking {
        val versus = start()
        repo.updateVersus(VersusSetup(redName = "Ayşe", blueName = "x".repeat(50), meSide = Side.SOLO))
        val stored = sessions.rows.value.single()
        assertEquals(versus.id, stored.id)
        assertEquals("Ayşe", stored.redName)
        assertEquals(FriendProfile.MAX_NAME, stored.blueName!!.length)
        assertNull(stored.meSide)
    }

    @Test
    fun noteSideOnlyInVersus() = runBlocking {
        assertNull(repo.addNote("solo", 100, Side.RED)!!.side)
        repo.endSession()
        start()
        assertEquals(Side.BLUE, repo.addNote("blue", 100, Side.BLUE)!!.side)
        assertNull(repo.addNote("shared", 100, Side.SOLO)!!.side)
    }

    @Test
    fun endingVersusReturnsToNoSession() = runBlocking {
        start()
        repo.addBeer(side = Side.RED)
        repo.endSession()
        assertNull(repo.openSession.first())
        assertEquals(Side.SOLO, repo.add().side)
    }
}
