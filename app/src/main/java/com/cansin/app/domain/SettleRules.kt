package com.cansin.app.domain

import com.cansin.app.data.Note
import java.util.Locale

/** Tab total for one person written on notes; person == null means no name on the note. */
data class PersonTotal(val person: String?, val minor: Long)

/** Settle up (P19): per-person totals, 50/50 shared split for versus, even split for a group. Plain math, no network. */
object SettleRules {
    const val MIN_SPLIT = 2
    const val MAX_SPLIT = 20

    /** Notes with an amount grouped by person (case-insensitive, first spelling kept), A–Z, unnamed last. */
    fun byPerson(notes: List<Note>): List<PersonTotal> {
        val named = linkedMapOf<String, PersonTotal>()
        var unnamed = 0L
        var hasUnnamed = false
        notes.filter { it.amountMinor != null }.forEach { note ->
            val amount = note.amountMinor ?: 0
            val person = note.person?.trim()?.takeIf { it.isNotEmpty() }
            if (person == null) {
                unnamed += amount
                hasUnnamed = true
            } else {
                val key = person.lowercase(Locale.ROOT)
                val prev = named[key]
                named[key] = PersonTotal(prev?.person ?: person, (prev?.minor ?: 0) + amount)
            }
        }
        val sorted = named.values.sortedBy { it.person!!.lowercase(Locale.ROOT) }
        return if (hasUnnamed) sorted + PersonTotal(null, unnamed) else sorted
    }

    /** Red/blue each pay their side plus half of shared; an odd cent goes to red. */
    fun splitShared(totals: TabTotals): Pair<Long, Long> {
        val half = totals.shared / 2
        return (totals.red + half + totals.shared % 2) to (totals.blue + half)
    }

    /** Even split rounded up to the cent, so the bill is always covered. */
    fun eachPays(total: Long, people: Int): Long {
        val n = people.coerceIn(MIN_SPLIT, MAX_SPLIT)
        return (total + n - 1) / n
    }
}
