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
    override fun observeHistory(): Flow<List<SessionSummary>> =
        rows.map { list -> list.filter { it.endedAt != null }.map { SessionSummary(it, 0, 0, 0) } }
    override fun observe(id: Long): Flow<Session?> = rows.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun deleteEnded(id: Long): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.id == id && it.endedAt != null }
        return before - rows.value.size
    }
    var deleteCalls = 0
    override suspend fun deleteEnded(ids: List<Long>): Int {
        deleteCalls++
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.id in ids && it.endedAt != null }
        return before - rows.value.size
    }
    val searchRows = MutableStateFlow(listOf<SearchRow>())
    override fun observeSearchText(): Flow<List<SearchRow>> = searchRows
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
    override suspend fun latestPrice(sessionId: Long, kind: DrinkKind, sizeMl: Int, serving: Serving?) =
        rows.value.filter { it.sessionId == sessionId && it.kind == kind && it.sizeMl == sizeMl && it.serving == serving && it.priceMinor != null }
            .maxWithOrNull(compareBy({ it.at }, { it.id }))?.priceMinor
    private fun BeerEvent.isLine(sessionId: Long, kind: DrinkKind, sizeMl: Int, side: Side, serving: Serving?) =
        this.sessionId == sessionId && this.kind == kind && this.sizeMl == sizeMl && this.side == side && this.serving == serving
    override suspend fun setPrice(sessionId: Long, kind: DrinkKind, sizeMl: Int, side: Side, serving: Serving?, priceMinor: Long?) {
        rows.value = rows.value.map { if (it.isLine(sessionId, kind, sizeMl, side, serving)) it.copy(priceMinor = priceMinor) else it }
    }
    override suspend fun setServing(sessionId: Long, kind: DrinkKind, sizeMl: Int, side: Side, from: Serving?, to: Serving?) {
        rows.value = rows.value.map { if (it.isLine(sessionId, kind, sizeMl, side, from)) it.copy(serving = to) else it }
    }
    override suspend fun latestMatching(sessionId: Long, side: Side, kind: DrinkKind, sizeMl: Int, serving: Serving?) =
        rows.value.filter { it.isLine(sessionId, kind, sizeMl, side, serving) }
            .maxWithOrNull(compareBy({ it.at }, { it.id }))
    override suspend fun totalPriceMinor(sessionId: Long) = rows.value.filter { it.sessionId == sessionId }.sumOf { it.priceMinor ?: 0 }
    override fun observeCount(sessionId: Long): Flow<Int> = rows.map { list -> list.count { it.sessionId == sessionId } }
    override fun observeEnded(): Flow<List<BeerEvent>> = rows
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
    override suspend fun totalMinor(sessionId: Long) = rows.value.filter { it.sessionId == sessionId }.sumOf { it.amountMinor ?: 0 }
    override suspend fun lastCreatedAt(sessionId: Long) = rows.value.filter { it.sessionId == sessionId }.maxOfOrNull { it.createdAt }
}

class FakeWaterDao : WaterDao {
    val rows = MutableStateFlow(listOf<WaterEvent>())
    private var nextId = 1L

    override suspend fun insert(event: WaterEvent): Long = nextId++.also { rows.value += event.copy(id = it) }
    override suspend fun delete(id: Long): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.id == id }
        return before - rows.value.size
    }
    override suspend fun count(sessionId: Long) = rows.value.count { it.sessionId == sessionId }
    override suspend fun lastAt(sessionId: Long) = rows.value.filter { it.sessionId == sessionId }.maxOfOrNull { it.at }
    override suspend fun countOnSide(sessionId: Long, side: Side?) = rows.value.count { it.sessionId == sessionId && it.side == side }
    override suspend fun latestPrice(sessionId: Long, sizeMl: Int) =
        rows.value.filter { it.sessionId == sessionId && it.sizeMl == sizeMl && it.priceMinor != null }.maxWithOrNull(compareBy({ it.at }, { it.id }))?.priceMinor
    override suspend fun setPrice(sessionId: Long, side: Side?, sizeMl: Int, priceMinor: Long?) {
        rows.value = rows.value.map { if (it.sessionId == sessionId && it.side == side && it.sizeMl == sizeMl) it.copy(priceMinor = priceMinor) else it }
    }
    override suspend fun latestSized(sessionId: Long, side: Side?, sizeMl: Int) =
        rows.value.filter { it.sessionId == sessionId && it.side == side && it.sizeMl == sizeMl }.maxWithOrNull(compareBy({ it.at }, { it.id }))
    override suspend fun totalPriceMinor(sessionId: Long) = rows.value.filter { it.sessionId == sessionId }.sumOf { it.priceMinor ?: 0 }
    override suspend fun latest(sessionId: Long, side: Side?) =
        rows.value.filter { it.sessionId == sessionId && it.side == side }.maxWithOrNull(compareBy({ it.at }, { it.id }))
    override fun observe(sessionId: Long): Flow<List<WaterEvent>> = rows.map { list -> list.filter { it.sessionId == sessionId } }
}

class FakeGroupSeatDao : GroupSeatDao {
    val rows = MutableStateFlow(listOf<GroupSeat>())
    override suspend fun seats(sessionId: Long) = rows.value.filter { it.sessionId == sessionId }
    override fun observe(sessionId: Long): Flow<List<GroupSeat>> = rows.map { l -> l.filter { it.sessionId == sessionId } }
    override suspend fun all() = rows.value
    override suspend fun insert(seats: List<GroupSeat>) {
        rows.value += seats
    }
    override suspend fun clear(sessionId: Long) {
        rows.value = rows.value.filterNot { it.sessionId == sessionId }
    }
}

suspend fun SessionRepository.add(finishPrevious: Boolean = false, size: BeerSize? = null): BeerEvent =
    checkNotNull(addBeer(finishPrevious, size))

class FakeFriendResultDao : FriendResultDao {
    val rows = MutableStateFlow(listOf<FriendResult>())
    override fun observeAll(): Flow<List<FriendResult>> = rows
    override suspend fun all() = rows.value
    override suspend fun insert(result: FriendResult): Long = (rows.value.size + 1L).also { rows.value += result.copy(id = it) }
    override suspend fun delete(id: Long): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.id == id }
        return before - rows.value.size
    }
}
