package com.cansin.app.domain

/**
 * Strict, small JSON reader for our own export files (org.json is stubbed in JVM unit tests).
 * Objects → Map, arrays → List, numbers → Long or Double. Limits depth and size; throws on anything odd.
 */
class MiniJson private constructor(private val s: String) {
    private var i = 0

    class Error(message: String) : Exception(message)

    companion object {
        const val MAX_DEPTH = 8

        fun parse(text: String): Any? {
            val p = MiniJson(text)
            val v = p.value(0)
            p.space()
            if (p.i != text.length) throw Error("trailing data")
            return v
        }
    }

    private fun value(depth: Int): Any? {
        if (depth > MAX_DEPTH) throw Error("too deep")
        space()
        if (i >= s.length) throw Error("end")
        return when (val c = s[i]) {
            '{' -> obj(depth)
            '[' -> arr(depth)
            '"' -> str()
            't' -> word("true", true)
            'f' -> word("false", false)
            'n' -> word("null", null)
            else -> if (c == '-' || c in '0'..'9') num() else throw Error("unexpected $c")
        }
    }

    private fun obj(depth: Int): Map<String, Any?> {
        i++
        val out = LinkedHashMap<String, Any?>()
        space()
        if (peek() == '}') { i++; return out }
        while (true) {
            space()
            if (peek() != '"') throw Error("key")
            val key = str()
            space()
            expect(':')
            if (out.containsKey(key)) throw Error("duplicate key")
            out[key] = value(depth + 1)
            space()
            when (next()) {
                ',' -> continue
                '}' -> return out
                else -> throw Error("object")
            }
        }
    }

    private fun arr(depth: Int): List<Any?> {
        i++
        val out = ArrayList<Any?>()
        space()
        if (peek() == ']') { i++; return out }
        while (true) {
            out.add(value(depth + 1))
            space()
            when (next()) {
                ',' -> continue
                ']' -> return out
                else -> throw Error("array")
            }
        }
    }

    private fun str(): String {
        i++
        val b = StringBuilder()
        while (true) {
            val c = next()
            when {
                c == '"' -> return b.toString()
                c == '\\' -> when (val e = next()) {
                    '"', '\\', '/' -> b.append(e)
                    'n' -> b.append('\n')
                    'r' -> b.append('\r')
                    't' -> b.append('\t')
                    'b' -> b.append('\b')
                    'f' -> b.append('\u000c')
                    'u' -> {
                        if (i + 4 > s.length) throw Error("escape")
                        b.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: throw Error("escape"))
                        i += 4
                    }
                    else -> throw Error("escape")
                }
                c < ' ' -> throw Error("control char")
                else -> b.append(c)
            }
        }
    }

    private fun num(): Any {
        val start = i
        if (peek() == '-') i++
        while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
        val t = s.substring(start, i)
        if (t.length > 24) throw Error("number")
        return t.toLongOrNull() ?: t.toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw Error("number")
    }

    private fun word(w: String, v: Any?): Any? {
        if (!s.startsWith(w, i)) throw Error("word")
        i += w.length
        return v
    }

    private fun space() {
        while (i < s.length && s[i] in " \t\r\n") i++
    }

    private fun peek(): Char? = s.getOrNull(i)
    private fun next(): Char = s.getOrNull(i++) ?: throw Error("end")
    private fun expect(c: Char) {
        if (next() != c) throw Error("expected $c")
    }
}
