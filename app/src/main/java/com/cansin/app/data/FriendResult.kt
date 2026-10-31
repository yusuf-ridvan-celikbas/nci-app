package com.cansin.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.cansin.app.domain.ResultCard
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** A friend's imported session result (P13). Day stored as ISO yyyy-MM-dd. Same card twice = ignored. */
@Entity(
    tableName = "friend_result",
    indices = [Index(value = ["name", "day", "beers", "durationMin", "water"], unique = true)],
)
data class FriendResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val day: String,
    val beers: Int,
    val durationMin: Int,
    val water: Int,
    val importedAt: Long,
) {
    val card: ResultCard? get() = runCatching { ResultCard(name, LocalDate.parse(day), beers, durationMin, water) }.getOrNull()

    companion object {
        fun of(card: ResultCard, importedAt: Long) =
            FriendResult(name = card.name, day = card.day.toString(), beers = card.beers, durationMin = card.durationMin, water = card.water, importedAt = importedAt)
    }
}

@Dao
interface FriendResultDao {
    @Query("SELECT * FROM friend_result ORDER BY day DESC, importedAt DESC, id DESC")
    fun observeAll(): Flow<List<FriendResult>>

    @Query("SELECT * FROM friend_result ORDER BY day, id")
    suspend fun all(): List<FriendResult>

    /** Returns -1 when the same result was already imported. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(result: FriendResult): Long

    @Query("DELETE FROM friend_result WHERE id = :id")
    suspend fun delete(id: Long): Int
}
