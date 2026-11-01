package com.cansin.app.domain

import com.cansin.app.data.CustomQuestion
import com.cansin.app.data.QuestionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuestionImportTest {
    @Test
    fun kindWordsInBothLanguages() {
        assertEquals(QuestionKind.TRUTH, QuestionImport.kindOf(" Truth "))
        assertEquals(QuestionKind.TRUTH, QuestionImport.kindOf("DOĞRULUK"))
        assertEquals(QuestionKind.TRUTH, QuestionImport.kindOf("dogruluk"))
        assertEquals(QuestionKind.DARE, QuestionImport.kindOf("Cesaret"))
        assertEquals(QuestionKind.DARE, QuestionImport.kindOf("dare"))
        assertNull(QuestionImport.kindOf("d"))
        assertNull(QuestionImport.kindOf("type"))
    }

    @Test
    fun readsCommaAndSemicolonFilesWithHeaderAndQuotes() {
        val comma = QuestionImport.BOM + "type,question\r\nTruth,\"What is your dream, really?\"\r\nDare,\"Say \"\"hi\"\" twice\"\r\n"
        val r = QuestionImport.parse(comma, emptyList(), 300)
        assertEquals(
            listOf(ImportedQuestion(QuestionKind.TRUTH, "What is your dream, really?"), ImportedQuestion(QuestionKind.DARE, "Say \"hi\" twice")),
            r.questions,
        )
        assertEquals(0, r.invalid)
        val semi = "Tür;Soru\nDoğruluk;En sevdiğin şarkı ne?\nCesaret;Bir fıkra anlat\n"
        assertEquals(2, QuestionImport.parse(semi, emptyList(), 300).questions.size)
        assertEquals(1, QuestionImport.parse("Truth\tLine one\n", emptyList(), 300).questions.size)
    }

    @Test
    fun quotedFieldsMayContainLineBreaks() {
        val r = QuestionImport.parse("Truth,\"two\nlines\"\nDare,ok\n", emptyList(), 300)
        assertEquals(listOf("two lines", "ok"), r.questions.map { it.text })
    }

    @Test
    fun countsInvalidDuplicatesAndLimit() {
        val existing = listOf(CustomQuestion(1, QuestionKind.TRUTH, "Old one?", 0))
        val text = listOf(
            "Truth,Old one?",          // duplicate of existing (case-insensitive)
            "Truth,New one?",
            "truth,new ONE?",          // duplicate within file
            "Maybe,What?",             // bad type
            "Dare,",                   // empty text
            "Dare," + "x".repeat(201), // too long
            "Dare,A",
            "Dare,B",
        ).joinToString("\n")
        val r = QuestionImport.parse(text, existing, capacity = 2)
        assertEquals(listOf("New one?", "A"), r.questions.map { it.text })
        assertEquals(3, r.invalid)
        assertEquals(2, r.duplicates)
        assertEquals(1, r.overLimit)
    }

    @Test
    fun templateRoundTrips() {
        val t = QuestionImport.template("type" to "question", listOf("Truth" to "Why, \"really\"?", "Dare" to "Sing"))
        val r = QuestionImport.parse(t, emptyList(), 300)
        assertEquals(listOf(ImportedQuestion(QuestionKind.TRUTH, "Why, \"really\"?"), ImportedQuestion(QuestionKind.DARE, "Sing")), r.questions)
    }
}
