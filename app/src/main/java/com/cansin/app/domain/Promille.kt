package com.cansin.app.domain

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class Drink(val at: Long, val finishedAt: Long?, val sizeMl: Int, val abv: Double)

data class PromilleEstimate(val current: Double, val peak: Double, val zeroAt: Long?)

object PromilleModel {
    const val MINUTE_MS = 60_000L
    const val DEFAULT_DRINK_MS = 20 * MINUTE_MS
    const val ABSORPTION_TAIL_MS = 30 * MINUTE_MS
    const val ELIMINATION_PER_MIN = 0.15 / 60
    private const val ETHANOL_DENSITY = 0.789
    private const val BLOOD_WATER = 0.8
    private const val BLOOD_DENSITY = 1.055
    private const val MAX_MINUTES = 72 * 60

    fun grams(sizeMl: Int, abv: Double): Double = sizeMl * abv / 100 * ETHANOL_DENSITY

    fun totalBodyWater(profile: BodyProfile): Double = when (profile.sex) {
        Sex.MALE -> 2.447 - 0.09516 * profile.age + 0.1074 * profile.heightCm + 0.3362 * profile.weightKg
        Sex.FEMALE, Sex.UNSPECIFIED -> -2.097 + 0.1069 * profile.heightCm + 0.2466 * profile.weightKg
    }

    fun drinkEnd(drinks: List<Drink>, index: Int): Long {
        val drink = drinks[index]
        drink.finishedAt?.let { return max(it, drink.at) }
        val fallback = drink.at + DEFAULT_DRINK_MS
        val next = drinks.getOrNull(index + 1)?.at
        return if (next != null && next > drink.at) min(fallback, next) else fallback
    }

    fun estimate(profile: BodyProfile, drinks: List<Drink>, now: Long): PromilleEstimate {
        val sorted = drinks.sortedBy { it.at }
        if (sorted.isEmpty()) return PromilleEstimate(0.0, 0.0, null)
        val tbw = totalBodyWater(profile)
        val windows = sorted.indices.map { i ->
            val start = sorted[i].at
            val end = drinkEnd(sorted, i) + ABSORPTION_TAIL_MS
            Triple(start, end, grams(sorted[i].sizeMl, sorted[i].abv))
        }
        val start = sorted.first().at
        val absorbedUntil = windows.maxOf { it.second }
        var bac = 0.0
        var peak = 0.0
        var current = 0.0
        var zeroAt: Long? = null
        for (step in 0 until MAX_MINUTES) {
            val from = start + step * MINUTE_MS
            val to = from + MINUTE_MS
            val absorbed = windows.sumOf { (s, e, g) -> g * overlap(from, to, s, e) / (e - s) }
            bac = max(0.0, bac + absorbed * BLOOD_WATER / tbw - ELIMINATION_PER_MIN)
            peak = max(peak, bac)
            if (to <= now) current = bac
            if (to >= absorbedUntil && bac == 0.0) {
                zeroAt = to
                break
            }
        }
        return PromilleEstimate(toPromille(current), toPromille(peak), zeroAt?.takeIf { it > now })
    }

    fun toPromille(gramsPerLitre: Double): Double = ceil(gramsPerLitre / BLOOD_DENSITY * 100 - 1e-9) / 100

    private fun overlap(from: Long, to: Long, start: Long, end: Long): Long = max(0L, min(to, end) - max(from, start))
}
