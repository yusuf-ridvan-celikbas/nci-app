package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.GroupSeat
import com.cansin.app.data.Side
import com.cansin.app.data.WaterEvent

/** Per-seat times for the Together rows. All plain information: no warnings, no ranking. */
data class SeatTimes(
    val side: Side,
    val drinks: Int,
    val water: Int,
    val lastDrinkAt: Long?,
    val lastWaterAt: Long?,
    /** First drink or water of this person: "at the table" starts here. */
    val firstAt: Long?,
    /** Latest drink, for the timer (only if not finished yet). */
    val openDrink: BeerEvent?,
)

/** A running drink timer for one seat (not me: my seat uses the normal [TimerState]). */
data class SeatTimer(val side: Side, val beerId: Long, val startedAt: Long)

object SeatTimeRules {
    const val MINUTE_MS = 60_000L
    private const val MAX_RAW = 1_000

    fun times(seats: List<GroupSeat>, beers: List<BeerEvent>, water: List<WaterEvent>): List<SeatTimes> =
        GroupRules.ordered(seats).map { seat ->
            val mine = beers.filter { it.side == seat.side }
            val glasses = water.filter { it.side == seat.side }
            val latest = mine.maxWithOrNull(compareBy({ it.at }, { it.id }))
            SeatTimes(
                side = seat.side,
                drinks = mine.size,
                water = glasses.size,
                lastDrinkAt = latest?.at,
                lastWaterAt = glasses.maxOfOrNull { it.at },
                firstAt = (mine.map { it.at } + glasses.map { it.at }).minOrNull(),
                openDrink = latest?.takeIf { it.finishedAt == null },
            )
        }

    /** Whole minutes since [at], never negative (clock changes). */
    fun minutesSince(at: Long, now: Long): Long = ((now - at) / MINUTE_MS).coerceAtLeast(0)

    /** A seat timer can start on its latest unfinished drink when none runs for that seat. */
    fun canStart(enabled: Boolean, running: SeatTimer?, times: SeatTimes): Boolean =
        enabled && running == null && times.openDrink != null

    /** Timer only counts while it still belongs to the seat's latest open drink. */
    fun isValid(timer: SeatTimer, times: SeatTimes): Boolean = times.openDrink?.id == timer.beerId

    /** "sessionId|SIDE:beerId:startedAt,…" — stored in DataStore while one group session is open. */
    fun encode(sessionId: Long, timers: Collection<SeatTimer>): String =
        "$sessionId|" + timers.sortedBy { GroupRules.SIDES.indexOf(it.side) }.joinToString(",") { "${it.side.name}:${it.beerId}:${it.startedAt}" }

    /** Strict: wrong session, unknown side, bad numbers or duplicates → that entry (or all) is dropped. */
    fun decode(raw: String?, sessionId: Long): Map<Side, SeatTimer> {
        if (raw == null || raw.length > MAX_RAW) return emptyMap()
        val bar = raw.indexOf('|')
        if (bar < 0 || raw.substring(0, bar).toLongOrNull() != sessionId) return emptyMap()
        val body = raw.substring(bar + 1)
        if (body.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Side, SeatTimer>()
        for (part in body.split(',')) {
            val f = part.split(':')
            if (f.size != 3) continue
            val side = Side.entries.firstOrNull { it.name == f[0] }?.takeIf { GroupRules.isSeatSide(it) } ?: continue
            val beerId = f[1].toLongOrNull()?.takeIf { it > 0 } ?: continue
            val startedAt = f[2].toLongOrNull()?.takeIf { it >= 0 } ?: continue
            if (side !in out) out[side] = SeatTimer(side, beerId, startedAt)
        }
        return out
    }
}
