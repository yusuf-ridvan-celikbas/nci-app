package com.cansin.app.domain

object WaterRules {
    const val DEFAULT_EVERY = 2
    const val MIN_EVERY = 1
    const val MAX_EVERY = 10

    fun clamp(every: Int): Int = every.coerceIn(MIN_EVERY, MAX_EVERY)

    fun beersSinceWater(myBeerTimes: List<Long>, lastWaterAt: Long?): Int =
        myBeerTimes.count { lastWaterAt == null || it > lastWaterAt }

    fun shouldNudge(enabled: Boolean, every: Int, since: Int, dismissedAt: Int?): Boolean =
        enabled && since >= clamp(every) && (dismissedAt == null || since > dismissedAt)
}
