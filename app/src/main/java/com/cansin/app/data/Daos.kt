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
            // Compressed nights (P27) keep stored totals; live ones count their rows.
            "CASE WHEN s.t_compressedAt IS NOT NULL THEN s.t_drinks ELSE (SELECT COUNT(*) FROM beer_event b WHERE b.sessionId = s.id) END AS beers, " +
            "CASE WHEN s.t_compressedAt IS NOT NULL THEN s.t_notes ELSE (SELECT COUNT(*) FROM note n WHERE n.sessionId = s.id) END AS notes, " +
            "CASE WHEN s.t_compressedAt IS NOT NULL THEN s.t_water ELSE (SELECT COUNT(*) FROM water_event w WHERE w.sessionId = s.id) END AS water, " +
            "CASE WHEN s.t_compressedAt IS NOT NULL THEN s.t_spentMinor ELSE (SELECT COALESCE(SUM(n2.amountMinor), 0) FROM note n2 WHERE n2.sessionId = s.id) + " +
            "(SELECT COALESCE(SUM(b2.priceMinor), 0) FROM beer_event b2 WHERE b2.sessionId = s.id) END AS spentMinor " +
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

    @Query("SELECT * FROM water_event WHERE sessionId = :sessionId AND side IS :side ORDER BY at DESC, id DESC LIMIT 1")
    suspend fun latest(sessionId: Long, side: Side?): WaterEvent?

    @Query("SELECT COUNT(*) FROM water_event WHERE sessionId = :sessionId AND side IS :side")
    suspend fun countOnSide(sessionId: Long, side: Side?): Int

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

    @Insert
    suspend fun insertSeats(seats: List<GroupSeat>)

    @Query("DELETE FROM custom_question")
    suspend fun clearCustomQuestions()

    @Insert
    suspend fun insertCustomQuestions(questions: List<CustomQuestion>)
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

    /** P28: tonight's price for this drink (latest priced one), so a price edited on the Tab sticks. */
    @Query("SELECT priceMinor FROM beer_event WHERE sessionId = :sessionId AND kind = :kind AND sizeMl = :sizeMl AND brand IS :brand AND priceMinor IS NOT NULL ORDER BY at DESC, id DESC LIMIT 1")
    suspend fun latestPrice(sessionId: Long, kind: DrinkKind, sizeMl: Int, brand: String?): Long?

    @Query("UPDATE beer_event SET priceMinor = :priceMinor WHERE sessionId = :sessionId AND kind = :kind AND sizeMl = :sizeMl AND side = :side AND brand IS :brand")
    suspend fun setPrice(sessionId: Long, kind: DrinkKind, sizeMl: Int, side: Side, brand: String?, priceMinor: Long?)

    @Query("SELECT * FROM beer_event WHERE sessionId = :sessionId AND side = :side AND kind = :kind AND sizeMl = :sizeMl AND brand IS :brand ORDER BY at DESC, id DESC LIMIT 1")
    suspend fun latestMatching(sessionId: Long, side: Side, kind: DrinkKind, sizeMl: Int, brand: String?): BeerEvent?

    /** P29: brands used lately, newest first (for the picker). */
    @Query("SELECT brand FROM beer_event WHERE brand IS NOT NULL GROUP BY brand ORDER BY MAX(at) DESC LIMIT 8")
    fun observeRecentBrands(): Flow<List<String>>

    /** P28: drinks on the Tab. */
    @Query("SELECT COALESCE(SUM(priceMinor), 0) FROM beer_event WHERE sessionId = :sessionId")
    suspend fun totalPriceMinor(sessionId: Long): Long

    @Query("SELECT COUNT(*) FROM beer_event WHERE sessionId = :sessionId")
    fun observeCount(sessionId: Long): Flow<Int>

    @Query("SELECT * FROM beer_event WHERE sessionId = :sessionId ORDER BY at, id")
    fun observe(sessionId: Long): Flow<List<BeerEvent>>

    @Query("SELECT b.* FROM beer_event b JOIN session s ON s.id = b.sessionId WHERE s.endedAt IS NOT NULL ORDER BY b.at, b.id")
    fun observeEnded(): Flow<List<BeerEvent>>

    @Query("UPDATE beer_event SET at = :at, finishedAt = :finishedAt, sizeMl = :sizeMl, abv = :abv WHERE id = :id")
    suspend fun update(id: Long, at: Long, finishedAt: Long?, sizeMl: Int, abv: Double): Int
}
