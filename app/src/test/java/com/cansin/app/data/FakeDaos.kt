package com.cansin.app.data

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
}

class FakeBeerDao : BeerDao {
    val rows = MutableStateFlow(listOf<BeerEvent>())
    private var nextId = 1L

    override suspend fun insert(event: BeerEvent): Long = nextId++.also { rows.value += event.copy(id = it) }
    override suspend fun latest(sessionId: Long) =
        rows.value.filter { it.sessionId == sessionId }.maxWithOrNull(compareBy({ it.at }, { it.id }))
    override suspend fun delete(id: Long): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.id == id }
        return before - rows.value.size
    }
    override suspend fun count(sessionId: Long) = rows.value.count { it.sessionId == sessionId }
    override fun observeCount(sessionId: Long): Flow<Int> = rows.map { list -> list.count { it.sessionId == sessionId } }
}
