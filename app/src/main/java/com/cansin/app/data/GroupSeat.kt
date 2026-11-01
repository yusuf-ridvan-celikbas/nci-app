package com.cansin.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** P23: one seat of a "together" session. Seat order = [com.cansin.app.domain.GroupRules.SIDES]; name null = default label. */
@Entity(
    tableName = "group_seat",
    primaryKeys = ["sessionId", "side"],
    foreignKeys = [ForeignKey(Session::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
)
data class GroupSeat(val sessionId: Long, val side: Side, val name: String? = null)

@Dao
interface GroupSeatDao {
    @Query("SELECT * FROM group_seat WHERE sessionId = :sessionId")
    suspend fun seats(sessionId: Long): List<GroupSeat>

    @Query("SELECT * FROM group_seat WHERE sessionId = :sessionId")
    fun observe(sessionId: Long): Flow<List<GroupSeat>>

    @Query("SELECT * FROM group_seat")
    suspend fun all(): List<GroupSeat>

    @Insert
    suspend fun insert(seats: List<GroupSeat>)

    @Query("DELETE FROM group_seat WHERE sessionId = :sessionId")
    suspend fun clear(sessionId: Long)
}
