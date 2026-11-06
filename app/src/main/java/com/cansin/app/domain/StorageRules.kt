package com.cansin.app.domain

import androidx.annotation.StringRes
import com.cansin.app.R
import com.cansin.app.data.BeerEvent
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.SessionTotals
import com.cansin.app.data.Side
import com.cansin.app.data.WaterEvent
import java.time.Instant
import java.time.ZoneId

/** P27: how long nights stay at all. Months = calendar months back from today. */
enum class KeepHistory(@StringRes val label: Int, val months: Long?) {
    FOREVER(R.string.keep_forever, null),
    YEAR_1(R.string.keep_year_1, 12),
    MONTHS_6(R.string.keep_months_6, 6),
    MONTHS_3(R.string.keep_months_3, 3),
}

/** P27: after how long a night keeps only its totals. */
enum class CompressAfter(@StringRes val label: Int, val months: Long?) {
    NEVER(R.string.compress_never, null),
    MONTH_1(R.string.compress_month_1, 1),
    MONTHS_3(R.string.compress_months_3, 3),
    MONTHS_6(R.string.compress_months_6, 6),
}

/** Drinks and water of one side/seat in a compressed night. */
data class SideCount(val side: Side, val drinks: Int, val water: Int)

object StorageRules {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val MAX_COUNT = 1_000
    const val MAX_SPENT_MINOR = 1_000_000_000L
    const val MAX_PEAK = 5.0
    const val MAX_PACE_MIN = 72.0 * 60
    private const val MAX_SIDES_RAW = 400

    /** Start of the day [months] calendar months before today; nights that started earlier are affected. Null = never. */
    fun cutoff(now: Long, months: Long?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (months == null) return null
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return today.minusMonths(months).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun isDue(lastRun: Long?, now: Long): Boolean = lastRun == null || now - lastRun >= DAY_MS || now < lastRun

    /** Totals that keep History, Stats, the calendar and the share card the same after the rows are gone. */
    fun summarize(
        session: Session,
        beers: List<BeerEvent>,
        water: List<WaterEvent>,
        notes: List<Note>,
        profile: BodyProfile?,
        now: Long,
    ): SessionTotals {
        val me = VersusRules.meSide(session)
        val stats = StatsRules.session(session, beers, profile)
        val sides = if (VersusRules.hasSides(session.mode)) {
            val order = (beers.map { it.side } + water.mapNotNull { it.side }).distinct()
                .filter { VersusRules.fits(it, session.mode) }
                .sortedBy { com.cansin.app.domain.GroupRules.SIDES.indexOf(it) }
            encodeSides(order.map { side -> SideCount(side, beers.count { it.side == side }, water.count { it.side == side }) })
        } else {
            null
        }
        return SessionTotals(
            compressedAt = now,
            drinks = beers.size.coerceAtMost(MAX_COUNT),
            mine = me?.let { beers.count { b -> VersusRules.isMine(b, session) }.coerceAtMost(MAX_COUNT) },
            water = water.size.coerceAtMost(MAX_COUNT),
            myWater = me?.let { water.count { w -> VersusRules.isMineWater(w, session) }.coerceAtMost(MAX_COUNT) },
            notes = notes.size.coerceAtMost(MAX_COUNT),
            spentMinor = (notes.sumOf { it.amountMinor ?: 0 } + TabRules.drinkTotal(beers) + TabRules.waterTotal(water)).coerceIn(0, MAX_SPENT_MINOR),
            paceMin = stats?.paceMin,
            peak = stats?.peak,
            sides = sides,
        )
    }

    /** "RED:3:1,BLUE:2:0" (side:drinks:water). */
    fun encodeSides(counts: List<SideCount>): String? =
        counts.takeIf { it.isNotEmpty() }?.joinToString(",") { "${it.side.name}:${it.drinks}:${it.water}" }

    /** Strict: unknown/SOLO sides, bad numbers and duplicates are dropped. */
    fun decodeSides(raw: String?): List<SideCount> {
        if (raw.isNullOrEmpty() || raw.length > MAX_SIDES_RAW) return emptyList()
        val out = LinkedHashMap<Side, SideCount>()
        for (part in raw.split(',')) {
            val f = part.split(':')
            if (f.size != 3) continue
            val side = Side.entries.firstOrNull { it.name == f[0] }?.takeIf { it != Side.SOLO } ?: continue
            val drinks = f[1].toIntOrNull()?.takeIf { it in 0..MAX_COUNT } ?: continue
            val water = f[2].toIntOrNull()?.takeIf { it in 0..MAX_COUNT } ?: continue
            if (side !in out) out[side] = SideCount(side, drinks, water)
        }
        return out.values.toList()
    }

    /** Range check for totals read from a backup file. */
    fun valid(t: SessionTotals): Boolean =
        t.compressedAt >= 0 &&
            t.drinks in 0..MAX_COUNT && t.water in 0..MAX_COUNT && t.notes in 0..MAX_COUNT &&
            (t.mine == null || t.mine in 0..t.drinks) &&
            (t.myWater == null || t.myWater in 0..t.water) &&
            t.spentMinor in 0..MAX_SPENT_MINOR &&
            (t.paceMin == null || t.paceMin in 0.0..MAX_PACE_MIN) &&
            (t.peak == null || t.peak in 0.0..MAX_PEAK) &&
            (t.sides == null || decodeSides(t.sides).isNotEmpty())
}
