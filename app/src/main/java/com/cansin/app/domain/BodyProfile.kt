package com.cansin.app.domain

enum class Sex { MALE, FEMALE, UNSPECIFIED }

enum class License { NONE, CANDIDATE, NORMAL, COMMERCIAL }

data class BodyProfile(
    val sex: Sex,
    val age: Int,
    val heightCm: Int,
    val weightKg: Int,
    val license: License,
) {
    val isValid: Boolean get() = age in AGE && heightCm in HEIGHT_CM && weightKg in WEIGHT_KG

    companion object {
        val AGE = 18..100
        val HEIGHT_CM = 120..230
        val WEIGHT_KG = 35..250

        fun parse(raw: String, range: IntRange): Int? = raw.trim().takeIf { it.length <= 3 }?.toIntOrNull()?.takeIf { it in range }
    }
}
