package com.cansin.app.data

import com.cansin.app.domain.GroupSetup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GroupRepositoryTest {
    private val now = 1_000_000L
    private val sessions = FakeSessionDao()
    private val beers = FakeBeerDao()
    private val notes = FakeNoteDao()
    private val water = FakeWaterDao()
    private val seats = FakeGroupSeatDao()
    private val repo = SessionRepository(sessions, beers, notes, water, clock = { now }, seats = seats)

    @Test
    fun startGroupCreatesSeatsAndOnlyAcceptsThem() = runBlocking {
        val session = repo.startGroup(GroupSetup(listOf("Ali", "Can", "Deniz"), me = Side.BLUE))
        assertEquals(SessionMode.GROUP, session.mode)
        assertEquals(Side.BLUE, session.meSide)
        assertEquals(3, seats.rows.value.size)
        assertNotNull(repo.addBeer(side = Side.GREEN))
        assertNull(repo.addBeer(side = Side.PURPLE))
        assertNull(repo.addBeer())
        assertEquals(Side.BLUE, repo.addMyBeer()?.side)
        assertEquals(listOf(Side.GREEN, Side.BLUE), beers.rows.value.map { it.side })
    }

    @Test
    fun waterGoesToASeat() = runBlocking {
        repo.startGroup(GroupSetup(listOf("a", "b"), me = Side.RED))
        assertEquals(Side.BLUE, repo.addWater(Side.BLUE)?.side)
        assertEquals(Side.RED, repo.addWater()?.side)
        assertNull(repo.addWater(Side.GREEN))
        val snap = repo.snapshot()
        assertEquals(1, snap.water)
        assertEquals(true, snap.group)
    }

    @Test
    fun noMeMeansNoDefaultWater() = runBlocking {
        repo.startGroup(GroupSetup(listOf("a", "b")))
        assertNull(repo.addWater())
        assertNull(repo.addMyBeer())
    }

    @Test
    fun updateRenamesAndNeverDropsSeats() = runBlocking {
        repo.startGroup(GroupSetup(listOf("a", "b", "c")))
        repo.updateGroup(GroupSetup(listOf("x", "y"), me = Side.GREEN))
        assertEquals(listOf("x", "y", null), seats.rows.value.map { it.name })
        assertEquals(Side.GREEN, sessions.open()?.meSide)
    }

    @Test
    fun emptyGroupStaysOpenUntilEnded() = runBlocking {
        repo.startGroup(GroupSetup(listOf("a", "b")))
        val beer = repo.addBeer(side = Side.RED)!!
        repo.undo(beer)
        assertNotNull(sessions.open())
        repo.endSession()
        assertNull(sessions.open())
    }

    @Test
    fun soloWaterHasNoSide() = runBlocking {
        repo.addBeer()
        assertNull(repo.addWater(Side.RED)!!.side)
    }
}
