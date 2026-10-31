package com.cansin.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun open(): Session?

    @Query("SELECT * FROM session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeOpen(): Flow<Session?>

    @Insert
    suspend fun insert(session: Session): Long

    @Query("UPDATE session SET endedAt = :endedAt WHERE id = :id")
    suspend fun end(id: Long, endedAt: Long)

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BeerDao {
    @Insert
    suspend fun insert(event: BeerEvent): Long

    @Query("SELECT * FROM beer_event WHERE sessionId = :sessionId ORDER BY at DESC, id DESC LIMIT 1")
    suspend fun latest(sessionId: Long): BeerEvent?

    @Query("DELETE FROM beer_event WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("UPDATE beer_event SET finishedAt = :at WHERE id = :id AND finishedAt IS NULL")
    suspend fun finish(id: Long, at: Long)

    @Query("SELECT COUNT(*) FROM beer_event WHERE sessionId = :sessionId")
    suspend fun count(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM beer_event WHERE sessionId = :sessionId")
    fun observeCount(sessionId: Long): Flow<Int>
}
