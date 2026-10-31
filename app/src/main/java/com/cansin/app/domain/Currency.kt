package com.cansin.app.domain

/** Display-only currency symbol for tab amounts. Amounts stay plain minor units in the DB. */
enum class Currency(val symbol: String?) {
    NONE(null),
    TRY("₺"),
    EUR("€"),
    USD("$"),
    GBP("£");

    /** Turkish style puts the symbol after the number ("12,50 ₺"); $ and £ always go first. */
    fun symbolFirst(turkish: Boolean): Boolean = when (this) {
        NONE, TRY -> false
        EUR -> !turkish
        USD, GBP -> true
    }

    fun format(minor: Long, decimalSeparator: Char, turkish: Boolean): String {
        val number = NoteRules.formatAmount(minor, decimalSeparator)
        val s = symbol ?: return number
        return if (symbolFirst(turkish)) s + number else "$number $s"
    }
}
