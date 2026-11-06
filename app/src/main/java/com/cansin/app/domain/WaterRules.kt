package com.cansin.app.domain

object WaterRules {
    /** P33 glass sizes; 500 ml is the default. */
    val SIZES_ML = listOf(250, 500, 1_000)
    const val DEFAULT_ML = 500

    fun cleanSize(ml: Int?): Int = ml?.takeIf { it in SIZES_ML } ?: DEFAULT_ML

    const val DEFAULT_EVERY = 2
    const val MIN_EVERY = 1
    const val MAX_EVERY = 10

    fun clamp(every: Int): Int = every.coerceIn(MIN_EVERY, MAX_EVERY)

    fun beersSinceWater(myBeerTimes: List<Long>, lastWaterAt: Long?): Int =
        myBeerTimes.count { lastWaterAt == null || it > lastWaterAt }

    fun shouldNudge(enabled: Boolean, every: Int, since: Int, dismissedAt: Int?): Boolean =
        enabled && since >= clamp(every) && (dismissedAt == null || since > dismissedAt)
}
