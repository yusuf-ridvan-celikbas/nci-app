package com.cansin.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** GROUP = P23 "drinking together" (2–6 seats in group_seat). */
enum class SessionMode { SOLO, VERSUS, GROUP }

/** RED/BLUE = versus sides; group seats use [com.cansin.app.domain.GroupRules.SIDES]. */
enum class Side { SOLO, RED, BLUE, GREEN, PURPLE, AMBER, YELLOW, TEAL, PINK, ORANGE, GREY }

/** P34 beer serving: draft (fıçı) or bottle (şişe). */
enum class Serving { DRAFT, BOTTLE }

/** P22: what was drunk. Table and class keep the "beer" name to avoid a risky rename. */
enum class DrinkKind { BEER, WINE, RAKI, SHOT, COCKTAIL, CUSTOM }

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
    /** P27: set when the night was compressed (its drink, water and note rows were removed). */
    @Embedded(prefix = "t_") val totals: SessionTotals? = null,
)

/**
 * P27 totals kept for a compressed night. [mine]/[myWater] are null when there is no "me" (Versus without a side).
 * [sides] = per side/seat drinks and water, see [com.cansin.app.domain.StorageRules.encodeSides].
 */
data class SessionTotals(
    val compressedAt: Long,
    val drinks: Int,
    val mine: Int?,
    val water: Int,
    val myWater: Int?,
    val notes: Int,
    val spentMinor: Long,
    val paceMin: Double?,
    val peak: Double?,
    val sides: String?,
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
    @ColumnInfo(defaultValue = "BEER") val kind: DrinkKind = DrinkKind.BEER,
    /** P28: price saved when the drink was added (minor units); null = not on the Tab. */
    val priceMinor: Long? = null,
    /** P29 brand label; P36: no longer used, always cleared (column kept for the schema). */
    val brand: String? = null,
    /** P34: draft or bottle (beer only; null = not said / other kinds). */
    val serving: Serving? = null,
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
    /** Seat in a group session; null = me (solo/versus, and all rows before DB v5). */
    @ColumnInfo(defaultValue = "NULL") val side: Side? = null,
    /** P33: price saved when the glass was added (minor units); null = not on the Tab. */
    val priceMinor: Long? = null,
    /** P33: glass size (250 / 500 / 1000 ml; old rows = 500). */
    @ColumnInfo(defaultValue = "500") val sizeMl: Int = 500,
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
