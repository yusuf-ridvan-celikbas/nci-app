package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.GroupSeat
import com.cansin.app.data.Note
import com.cansin.app.data.Side
import com.cansin.app.data.WaterEvent

/** Names per seat (null = default label) and which seat is me, if any. */
data class GroupSetup(val names: List<String?>, val me: Side? = null)

/** Neutral per-seat numbers, always in seat order: never sorted by count, no leader. */
data class SeatTally(val side: Side, val drinks: Int, val water: Int)

/** What each seat pays: own notes plus an even share of shared notes. */
data class SeatPays(val side: Side, val own: Long, val pays: Long)

/** P23 "drinking together": 2–6 seats. Framed as a shared tab, not a race (store policy). */
object GroupRules {
    const val MIN_SEATS = 2
    const val MAX_SEATS = 6
    val SIDES = listOf(Side.RED, Side.BLUE, Side.GREEN, Side.PURPLE, Side.AMBER, Side.YELLOW)

    fun sides(count: Int): List<Side> = SIDES.take(count.coerceIn(MIN_SEATS, MAX_SEATS))

    fun isSeatSide(side: Side?): Boolean = side in SIDES

    /** Cleaned seats for a setup; extra names beyond MAX_SEATS are dropped, missing ones padded. */
    fun seats(sessionId: Long, setup: GroupSetup): List<GroupSeat> {
        val count = setup.names.size.coerceIn(MIN_SEATS, MAX_SEATS)
        return sides(count).mapIndexed { i, side -> GroupSeat(sessionId, side, VersusRules.cleanName(setup.names.getOrNull(i))) }
    }

    fun me(setup: GroupSetup): Side? = setup.me?.takeIf { it in sides(setup.names.size) }

    fun ordered(seats: List<GroupSeat>): List<GroupSeat> = seats.sortedBy { SIDES.indexOf(it.side) }

    fun tally(seats: List<GroupSeat>, beers: List<BeerEvent>, water: List<WaterEvent>): List<SeatTally> {
        val drinks = beers.groupingBy { it.side }.eachCount()
        val glasses = water.groupingBy { it.side }.eachCount()
        return ordered(seats).map { SeatTally(it.side, drinks[it.side] ?: 0, glasses[it.side] ?: 0) }
    }

    /** Notes on a seat count for it; notes without a seat (or a seat not at the table) are shared. */
    fun tab(seats: List<GroupSeat>, notes: List<Note>): Pair<Map<Side, Long>, Long> {
        val sides = seats.map { it.side }.toSet()
        val own = sides.associateWith { 0L }.toMutableMap()
        var shared = 0L
        notes.forEach { n ->
            val amount = n.amountMinor ?: 0
            val side = n.side
            if (side != null && side in sides) own[side] = own.getValue(side) + amount else shared += amount
        }
        return own to shared
    }

    /** Shared part split evenly; leftover cents go to the first seats so the sum is exact. */
    fun split(seats: List<GroupSeat>, notes: List<Note>, splitShared: Boolean): List<SeatPays> {
        val (own, shared) = tab(seats, notes)
        val ordered = ordered(seats)
        if (ordered.isEmpty()) return emptyList()
        val base = if (splitShared) shared / ordered.size else 0
        val extra = if (splitShared) (shared % ordered.size).toInt() else 0
        return ordered.mapIndexed { i, seat ->
            val mine = own[seat.side] ?: 0
            SeatPays(seat.side, mine, mine + base + if (i < extra) 1 else 0)
        }
    }
}
