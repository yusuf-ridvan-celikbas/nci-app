package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import kotlin.math.roundToInt

data class BeerSize(val sizeMl: Int, val abv: Double) {
    companion object {
        val DEFAULT = BeerSize(BeerEvent.DEFAULT_SIZE_ML, BeerEvent.DEFAULT_ABV)
    }
}

object BeerRules {
    val PRESETS_ML = listOf(330, 500, 1_000)
    const val MIN_ML = 20
    const val MAX_ML = 2_000
    const val MIN_ABV = 0.0
    const val MAX_ABV = 80.0
    private val ONE_DECIMAL = Regex("""^(\d{1,3})(?:[.,](\d))?$""")

    fun clamp(size: BeerSize) = BeerSize(size.sizeMl.coerceIn(MIN_ML, MAX_ML), size.abv.coerceIn(MIN_ABV, MAX_ABV))

    fun parseCl(raw: String): Int? = parseTenths(raw)?.takeIf { it in MIN_ML..MAX_ML }

    fun parseAbv(raw: String): Double? = parseTenths(raw)?.div(10.0)?.takeIf { it in MIN_ABV..MAX_ABV }

    fun formatCl(sizeMl: Int, decimalSeparator: Char): String = formatTenths(sizeMl, decimalSeparator, keepZero = false)

    fun formatAbv(abv: Double, decimalSeparator: Char): String =
        formatTenths((abv * 10).roundToInt(), decimalSeparator, keepZero = true)

    private fun parseTenths(raw: String): Int? {
        val match = ONE_DECIMAL.matchEntire(raw.trim()) ?: return null
        return match.groupValues[1].toInt() * 10 + (match.groupValues[2].toIntOrNull() ?: 0)
    }

    private fun formatTenths(tenths: Int, separator: Char, keepZero: Boolean): String =
        if (tenths % 10 == 0 && !keepZero) "${tenths / 10}" else "${tenths / 10}$separator${tenths % 10}"
}
