package com.cansin.app.data

import com.cansin.app.domain.SessionRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NoteRepositoryTest {
    private val sessions = FakeSessionDao()
    private val beers = FakeBeerDao()
    private val notes = FakeNoteDao()
    private val water = FakeWaterDao()
    private var now = 1_000_000L
    private val repo = SessionRepository(sessions, beers, notes, water, clock = { now })

    @Test
    fun noteStartsSessionWhenNoneOpen() = runBlocking {
        val note = repo.addNote("Ali paid", 2_500)
        assertNotNull(note)
        assertEquals(note!!.sessionId, repo.openSession.first()?.id)
        assertEquals(now, note.createdAt)
    }

    @Test
    fun blankNoteIsRejected() = runBlocking {
        assertNull(repo.addNote("   ", null))
        assertEquals(0, sessions.rows.value.size)
    }

    @Test
    fun noteJoinsOpenSession() = runBlocking {
        val beer = repo.add()
        assertEquals(beer.sessionId, repo.addNote("round", null)!!.sessionId)
    }

    @Test
    fun longTextAndPersonAreCapped() = runBlocking {
        val note = repo.addNote("x".repeat(2_000), null)!!
        assertEquals(Note.MAX_TEXT, note.text.length)
        repo.updateNote(note.copy(person = "  " + "p".repeat(50)))
        assertEquals(Note.MAX_PERSON, notes.rows.value.single().person!!.length)
    }

    @Test
    fun negativeAmountIsDropped() = runBlocking {
        assertNull(repo.addNote("bad", -5)!!.amountMinor)
    }

    @Test
    fun editKeepsOriginalCreatedAt() = runBlocking {
        val note = repo.addNote("beer", null)!!
        repo.updateNote(note.copy(editedTime = note.createdAt - 60_000))
        val stored = notes.rows.value.single()
        assertEquals(note.createdAt, stored.createdAt)
        assertEquals(note.createdAt - 60_000, stored.editedTime)
    }

    @Test
    fun sessionWithNotesIsNotDeletedWhenLastBeerUndone() = runBlocking {
        val beer = repo.add()
        repo.addNote("keep me", null)
        repo.undo(beer)
        assertEquals(beer.sessionId, repo.openSession.first()?.id)
    }

    @Test
    fun deletingLastNoteOfEmptySessionDeletesSession() = runBlocking {
        val note = repo.addNote("only", null)!!
        repo.deleteNote(note)
        assertEquals(0, sessions.rows.value.size)
    }

    @Test
    fun endingNotesOnlySessionKeepsIt() = runBlocking {
        val note = repo.addNote("tab", 1_000)!!
        repo.endSession()
        assertNotNull(sessions.rows.value.single { it.id == note.sessionId }.endedAt)
    }

    @Test
    fun noteCountsAsActivityForAutoClose() = runBlocking {
        val beer = repo.add()
        now += SessionRules.DEFAULT_INACTIVITY_MS - 1_000
        repo.addNote("still here", null)
        now += 2_000
        assertEquals(beer.sessionId, repo.add().sessionId)
        now += SessionRules.DEFAULT_INACTIVITY_MS
        assertNotEquals(beer.sessionId, repo.add().sessionId)
    }
}
