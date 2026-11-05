package com.cansin.app.domain

/** P29: optional drink brand (free text, e.g. "Efes"). Only a label: no % or price presets per brand. */
object BrandRules {
    const val MAX_LENGTH = 30
    const val MAX_RECENT = 8

    /** Quick picks in the drink picker (beer); the user's recent brands come first. */
    val SUGGESTED = listOf("Efes", "Tuborg", "Belfast", "Guinness", "Carlsberg", "Bomonti", "Heineken")

    /** Trimmed, inner spaces collapsed, no control/bidi characters, max [MAX_LENGTH]; blank = null. */
    fun clean(raw: String?): String? {
        if (raw == null) return null
        val t = raw.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty() || t.length > MAX_LENGTH) return null
        if (t.any { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }) return null
        return t
    }

    /** Recent first, then suggestions; duplicates (ignoring case) dropped. */
    fun choices(recent: List<String>): List<String> {
        val seen = HashSet<String>()
        return (recent + SUGGESTED).mapNotNull { clean(it) }.filter { seen.add(it.lowercase()) }
    }
}
