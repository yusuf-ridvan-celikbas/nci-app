package com.cansin.app.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class SessionSummary(
    @Embedded val session: Session,
    val beers: Int,
    val notes: Int,
    val water: Int,
    val spentMinor: Long = 0,
)

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

    @Update
    suspend fun update(session: Session)

    @Query(
        "SELECT s.*, " +
            "(SELECT COUNT(*) FROM beer_event b WHERE b.sessionId = s.id) AS beers, " +
            "(SELECT COUNT(*) FROM note n WHERE n.sessionId = s.id) AS notes, " +
            "(SELECT COUNT(*) FROM water_event w WHERE w.sessionId = s.id) AS water, " +
            "(SELECT COALESCE(SUM(n2.amountMinor), 0) FROM note n2 WHERE n2.sessionId = s.id) AS spentMinor " +
            "FROM session s WHERE s.endedAt IS NOT NULL ORDER BY s.startedAt DESC",
    )
    fun observeHistory(): Flow<List<SessionSummary>>

    @Query("SELECT * FROM session WHERE id = :id")
    fun observe(id: Long): Flow<Session?>

    @Query("DELETE FROM session WHERE id = :id AND endedAt IS NOT NULL")
    suspend fun deleteEnded(id: Long): Int
}

@Dao
interface WaterDao {
    @Insert
    suspend fun insert(event: WaterEvent): Long

    @Query("DELETE FROM water_event WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT COUNT(*) FROM water_event WHERE sessionId = :sessionId")
    suspend fun count(sessionId: Long): Int

    @Query("SELECT MAX(at) FROM water_event WHERE sessionId = :sessionId")
    suspend fun lastAt(sessionId: Long): Long?

    @Query("SELECT * FROM water_event WHERE sessionId = :sessionId ORDER BY at, id")
    fun observe(sessionId: Long): Flow<List<WaterEvent>>
}

@Dao
interface ExportDao {
    @Query("SELECT * FROM session ORDER BY startedAt")
    suspend fun sessions(): List<Session>

    @Query("SELECT * FROM beer_event ORDER BY at, id")
    suspend fun beers(): List<BeerEvent>

    @Query("SELECT * FROM water_event ORDER BY at, id")
    suspend fun water(): List<WaterEvent>

    @Query("SELECT * FROM note ORDER BY createdAt, id")
    suspend fun notes(): List<Note>

    @Query("SELECT * FROM friend_profile ORDER BY id")
    suspend fun friends(): List<FriendProfile>
}

/** Replace-all restore (P21); used only inside one transaction. */
@Dao
interface RestoreDao {
    @Query("DELETE FROM session")
    suspend fun clearSessions()

    @Query("DELETE FROM friend_profile")
    suspend fun clearFriends()

    @Query("DELETE FROM friend_result")
    suspend fun clearFriendResults()

    @Insert
    suspend fun insert(session: Session): Long

    @Insert
    suspend fun insert(friend: FriendProfile): Long

    @Insert
    suspend fun insertBeers(beers: List<BeerEvent>)

    @Insert
    suspend fun insertWater(water: List<WaterEvent>)

    @Insert
    suspend fun insertNotes(notes: List<Note>)

    @Insert
    suspend fun insertFriendResults(results: List<FriendResult>)
}

@Dao
interface FriendDao {
    @Query("SELECT * FROM friend_profile ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FriendProfile>>

    @Insert
    suspend fun insert(friend: FriendProfile): Long

    @Query("DELETE FROM friend_profile WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE session SET redFriendId = CASE WHEN redFriendId = :id THEN NULL ELSE redFriendId END, blueFriendId = CASE WHEN blueFriendId = :id THEN NULL ELSE blueFriendId END WHERE redFriendId = :id OR blueFriendId = :id")
    suspend fun unlink(id: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM note WHERE sessionId = :sessionId ORDER BY COALESCE(editedTime, createdAt), id")
    fun observe(sessionId: Long): Flow<List<Note>>

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Query("DELETE FROM note WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM note WHERE sessionId = :sessionId")
    suspend fun count(sessionId: Long): Int

    @Query("SELECT MAX(createdAt) FROM note WHERE sessionId = :sessionId")
    suspend fun lastCreatedAt(sessionId: Long): Long?

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM note WHERE sessionId = :sessionId")
    suspend fun totalMinor(sessionId: Long): Long
}

@Dao
interface BeerDao {
    @Insert
    suspend fun insert(event: BeerEvent): Long

    @Query("SELECT * FROM beer_event WHERE sessionId = :sessionId ORDER BY at DESC, id DESC LIMIT 1")
    suspend fun latest(sessionId: Long): BeerEvent?

    @Query("SELECT * FROM beer_event WHERE sessionId = :sessionId AND side = :side ORDER BY at DESC, id DESC LIMIT 1")
    suspend fun latestOnSide(sessionId: Long, side: Side): BeerEvent?

    @Query("SELECT COUNT(*) FROM beer_event WHERE sessionId = :sessionId AND side = :side")
    suspend fun countOnSide(sessionId: Long, side: Side): Int

    @Query("DELETE FROM beer_event WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("UPDATE beer_event SET finishedAt = :at WHERE id = :id AND finishedAt IS NULL")
    suspend fun finish(id: Long, at: Long)

    @Query("SELECT COUNT(*) FROM beer_event WHERE sessionId = :sessionId")
    suspend fun count(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM beer_event WHERE sessionId = :sessionId")
    fun observeCount(sessionId: Long): Flow<Int>

    @Query("SELECT * FROM beer_event WHERE sessionId = :sessionId ORDER BY at, id")
    fun observe(sessionId: Long): Flow<List<BeerEvent>>

    @Query("SELECT b.* FROM beer_event b JOIN session s ON s.id = b.sessionId WHERE s.endedAt IS NOT NULL ORDER BY b.at, b.id")
    fun observeEnded(): Flow<List<BeerEvent>>

    @Query("UPDATE beer_event SET at = :at, finishedAt = :finishedAt, sizeMl = :sizeMl, abv = :abv WHERE id = :id")
    suspend fun update(id: Long, at: Long, finishedAt: Long?, sizeMl: Int, abv: Double): Int
}
