package com.cansin.app.domain

import com.cansin.app.data.CustomQuestion
import com.cansin.app.data.QuestionKind
import java.util.Locale

data class ImportedQuestion(val kind: QuestionKind, val text: String)

data class QuestionImportResult(
    val questions: List<ImportedQuestion>,
    val invalid: Int,
    val duplicates: Int,
    val overLimit: Int,
)

/**
 * Own Truth or Dare questions from a CSV file (Excel/Sheets/Numbers "Save as CSV").
 * Two columns: type (Truth/Dare or Doğruluk/Cesaret) and the text. Comma, semicolon (Turkish Excel) or tab.
 * An optional header row is skipped. Strict: bad rows are counted and skipped, never guessed.
 */
object QuestionImport {
    const val MAX_BYTES = 256 * 1024
    const val BOM = 0xFEFF.toChar()
    private const val MAX_ROWS = 2_000
    private val TR = Locale.forLanguageTag("tr")
    private val LINE_BREAKS = Regex("[\r\n]+")

    private val TRUTH = setOf("truth", "doğruluk", "dogruluk")
    private val DARE = setOf("dare", "cesaret")

    fun kindOf(cell: String): QuestionKind? {
        val c = cell.trim()
        val keys = setOf(c.lowercase(Locale.ROOT), c.lowercase(TR))
        return when {
            keys.any { it in TRUTH } -> QuestionKind.TRUTH
            keys.any { it in DARE } -> QuestionKind.DARE
            else -> null
        }
    }

    /** Picks the delimiter that occurs most in the first line (outside quotes); comma if none. */
    fun delimiter(firstLine: String): Char {
        var inQuotes = false
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        for (ch in firstLine) {
            if (ch == '"') inQuotes = !inQuotes
            else if (!inQuotes && ch in counts) counts[ch] = counts.getValue(ch) + 1
        }
        return counts.entries.filter { it.value > 0 }.maxByOrNull { it.value }?.key ?: ','
    }

    /** RFC 4180-style rows: quoted fields may contain the delimiter, "" and line breaks. */
    fun rows(text: String): List<List<String>> {
        val body = text.removePrefix(BOM.toString())
        val delim = delimiter(body.substringBefore('\n'))
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        fun endField() { row.add(field.toString()); field.clear() }
        fun endRow() {
            endField()
            if (row.any { it.isNotBlank() }) rows.add(row)
            row = mutableListOf()
        }
        while (i < body.length && rows.size < MAX_ROWS) {
            val ch = body[i]
            when {
                inQuotes && ch == '"' && body.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && ch == delim -> endField()
                !inQuotes && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && body.getOrNull(i + 1) == '\n') i++
                    endRow()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    fun parse(text: String, existing: List<CustomQuestion>, capacity: Int): QuestionImportResult {
        val rows = rows(text)
        val seen = existing.map { key(it.kind, it.text) }.toMutableSet()
        val out = mutableListOf<ImportedQuestion>()
        var invalid = 0
        var duplicates = 0
        var overLimit = 0
        rows.forEachIndexed { index, cells ->
            val kind = cells.getOrNull(0)?.let(::kindOf)
            val clean = GameRules.cleanQuestion(cells.getOrNull(1)?.replace(LINE_BREAKS, " "))
            when {
                kind == null && index == 0 -> Unit // header row
                kind == null || clean == null || cells.getOrNull(1).orEmpty().trim().length > CustomQuestion.MAX_TEXT -> invalid++
                !seen.add(key(kind, clean)) -> duplicates++
                out.size >= capacity -> overLimit++
                else -> out.add(ImportedQuestion(kind, clean))
            }
        }
        return QuestionImportResult(out, invalid, duplicates, overLimit)
    }

    /** Template the user can open in Excel: header + one example per type, comma-separated, quoted. */
    fun template(header: Pair<String, String>, examples: List<Pair<String, String>>): String =
        (listOf(header) + examples).joinToString("\r\n", postfix = "\r\n") { (a, b) -> quote(a) + "," + quote(b) }

    private fun quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun key(kind: QuestionKind, text: String) = kind.name + "|" + text.lowercase(Locale.ROOT)
}
