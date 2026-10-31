package com.cansin.app.data

import com.cansin.app.domain.BeerSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSessionDao : SessionDao {
    val rows = MutableStateFlow(listOf<Session>())
    private var nextId = 1L

    override suspend fun open() = rows.value.filter { it.endedAt == null }.maxByOrNull { it.startedAt }
    override fun observeOpen(): Flow<Session?> = rows.map { list -> list.filter { it.endedAt == null }.maxByOrNull { it.startedAt } }
    override suspend fun insert(session: Session): Long = nextId++.also { rows.value += session.copy(id = it) }
    override suspend fun end(id: Long, endedAt: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(endedAt = endedAt) else it }
    }
    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
    override suspend fun update(session: Session) {
        rows.value = rows.value.map { if (it.id == session.id) session else it }
    }
}

class FakeBeerDao : BeerDao {
    val rows = MutableStateFlow(listOf<BeerEvent>())
    private var nextId = 1L

    override suspend fun insert(event: BeerEvent): Long = nextId++.also { rows.value += event.copy(id = it) }
    override suspend fun latest(sessionId: Long) =
        rows.value.filter { it.sessionId == sessionId }.maxWithOrNull(compareBy({ it.at }, { it.id }))
    override suspend fun latestOnSide(sessionId: Long, side: Side) =
        rows.value.filter { it.sessionId == sessionId && it.side == side }.maxWithOrNull(compareBy({ it.at }, { it.id }))
    override suspend fun countOnSide(sessionId: Long, side: Side) = rows.value.count { it.sessionId == sessionId && it.side == side }
    override suspend fun delete(id: Long): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.id == id }
        return before - rows.value.size
    }
    override suspend fun finish(id: Long, at: Long) {
        rows.value = rows.value.map { if (it.id == id && it.finishedAt == null) it.copy(finishedAt = at) else it }
    }
    override suspend fun count(sessionId: Long) = rows.value.count { it.sessionId == sessionId }
    override fun observeCount(sessionId: Long): Flow<Int> = rows.map { list -> list.count { it.sessionId == sessionId } }
    override fun observe(sessionId: Long): Flow<List<BeerEvent>> =
        rows.map { list -> list.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.at }, { it.id })) }
    override suspend fun update(id: Long, at: Long, finishedAt: Long?, sizeMl: Int, abv: Double): Int {
        val before = rows.value
        rows.value = before.map { if (it.id == id) it.copy(at = at, finishedAt = finishedAt, sizeMl = sizeMl, abv = abv) else it }
        return if (before.any { it.id == id }) 1 else 0
    }
}

class FakeNoteDao : NoteDao {
    val rows = MutableStateFlow(listOf<Note>())
    private var nextId = 1L

    override fun observe(sessionId: Long): Flow<List<Note>> =
        rows.map { list -> list.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.editedTime ?: it.createdAt }, { it.id })) }
    override suspend fun insert(note: Note): Long = nextId++.also { rows.value += note.copy(id = it) }
    override suspend fun update(note: Note) {
        rows.value = rows.value.map { if (it.id == note.id) note else it }
    }
    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
    override suspend fun count(sessionId: Long) = rows.value.count { it.sessionId == sessionId }
    override suspend fun lastCreatedAt(sessionId: Long) = rows.value.filter { it.sessionId == sessionId }.maxOfOrNull { it.createdAt }
}

suspend fun SessionRepository.add(finishPrevious: Boolean = false, size: BeerSize? = null): BeerEvent =
    checkNotNull(addBeer(finishPrevious, size))
