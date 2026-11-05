package com.cansin.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update

/** P27 storage maintenance: retention, compression and usage counts. Only ended sessions are touched. */
@Dao
interface StorageDao {
    @Query("SELECT COUNT(*) FROM session WHERE endedAt IS NOT NULL AND startedAt < :before")
    suspend fun countEndedBefore(before: Long): Int

    @Query("DELETE FROM session WHERE endedAt IS NOT NULL AND startedAt < :before")
    suspend fun deleteEndedBefore(before: Long): Int

    @Query("SELECT COUNT(*) FROM session WHERE endedAt IS NOT NULL AND t_compressedAt IS NULL AND startedAt < :before")
    suspend fun countUncompressedBefore(before: Long): Int

    @Query("SELECT * FROM session WHERE endedAt IS NOT NULL AND t_compressedAt IS NULL AND startedAt < :before ORDER BY startedAt")
    suspend fun uncompressedBefore(before: Long): List<Session>

    @Query("SELECT * FROM beer_event WHERE sessionId = :sessionId ORDER BY at, id")
    suspend fun beers(sessionId: Long): List<BeerEvent>

    @Query("SELECT * FROM water_event WHERE sessionId = :sessionId ORDER BY at, id")
    suspend fun water(sessionId: Long): List<WaterEvent>

    @Query("SELECT * FROM note WHERE sessionId = :sessionId ORDER BY createdAt, id")
    suspend fun notes(sessionId: Long): List<Note>

    @Query("SELECT * FROM group_seat WHERE sessionId = :sessionId")
    suspend fun seats(sessionId: Long): List<GroupSeat>

    @Update
    suspend fun update(session: Session)

    @Query("DELETE FROM beer_event WHERE sessionId = :sessionId")
    suspend fun deleteBeers(sessionId: Long)

    @Query("DELETE FROM water_event WHERE sessionId = :sessionId")
    suspend fun deleteWater(sessionId: Long)

    @Query("DELETE FROM note WHERE sessionId = :sessionId")
    suspend fun deleteNotes(sessionId: Long)

    @Query("SELECT COUNT(*) FROM session WHERE endedAt IS NOT NULL")
    suspend fun countEnded(): Int

    @Query("SELECT COUNT(*) FROM session WHERE t_compressedAt IS NOT NULL")
    suspend fun countCompressed(): Int

    @Query("SELECT COUNT(*) FROM custom_question")
    suspend fun countQuestions(): Int
}
