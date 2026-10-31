package com.cansin.app.domain

object TrDrivingLaw {
    const val LAW_VERSION = "TR-KTK-48"
    const val LAST_VERIFIED = "unverified (values from spec, verify before release)"
    val limits: Map<License, Double> = mapOf(
        License.CANDIDATE to 0.20,
        License.COMMERCIAL to 0.20,
        License.NORMAL to 0.50,
    )
}

enum class Greeting { MORNING, DAY, EVENING }

sealed interface Warning {
    data object Generic : Warning
    data class OverLimit(val limit: Double) : Warning
    data object UnderLimit : Warning
    data class Hello(val greeting: Greeting) : Warning
    data object SlowDown : Warning
    data object Poisoning : Warning
}

object WarningRules {
    const val SLOW_DOWN = 1.50
    const val POISONING = 3.00

    fun greeting(hour: Int): Greeting = when (hour) {
        in 5..11 -> Greeting.MORNING
        in 12..17 -> Greeting.DAY
        else -> Greeting.EVENING
    }

    fun warnings(promille: Double, license: License, turkish: Boolean, hour: Int): List<Warning> {
        if (!turkish) return listOf(Warning.Generic)
        val result = mutableListOf<Warning>()
        val limit = TrDrivingLaw.limits[license]
        when {
            limit == null -> result += Warning.Hello(greeting(hour))
            promille >= limit -> result += Warning.OverLimit(limit)
            promille > 0 -> result += Warning.UnderLimit
        }
        when {
            promille >= POISONING -> result += Warning.Poisoning
            promille >= SLOW_DOWN -> result += Warning.SlowDown
        }
        return result
    }
}
