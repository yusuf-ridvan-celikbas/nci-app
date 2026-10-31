package com.cansin.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SessionMode { SOLO, VERSUS }

enum class Side { SOLO, RED, BLUE }

@Entity(tableName = "session")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val mode: SessionMode = SessionMode.SOLO,
    val redName: String? = null,
    val blueName: String? = null,
    val redFriendId: Long? = null,
    val blueFriendId: Long? = null,
    val meSide: Side? = null,
)

@Entity(
    tableName = "beer_event",
    foreignKeys = [ForeignKey(Session::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class BeerEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val side: Side = Side.SOLO,
    val sizeMl: Int = DEFAULT_SIZE_ML,
    val abv: Double = DEFAULT_ABV,
    val at: Long,
    val finishedAt: Long? = null,
) {
    companion object {
        const val DEFAULT_SIZE_ML = 500
        const val DEFAULT_ABV = 5.0
    }
}

@Entity(
    tableName = "water_event",
    foreignKeys = [ForeignKey(Session::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class WaterEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val at: Long,
)

@Entity(
    tableName = "note",
    foreignKeys = [ForeignKey(Session::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val text: String,
    val person: String? = null,
    val amountMinor: Long? = null,
    val createdAt: Long,
    val editedTime: Long? = null,
    val side: Side? = null,
) {
    companion object {
        const val MAX_TEXT = 1000
        const val MAX_PERSON = 30
    }
}

@Entity(tableName = "friend_profile")
data class FriendProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
) {
    companion object {
        const val MAX_NAME = 30
    }
}
