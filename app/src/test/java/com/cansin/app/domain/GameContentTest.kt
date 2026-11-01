package com.cansin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Store policy guard for the built-in Truth or Dare content (P26): nothing about alcohol, EN/TR in step. */
class GameContentTest {
    private fun arrays(dir: String): Map<String, List<String>> {
        val file = File("src/main/res/$dir/game_questions.xml")
        assertTrue("missing ${file.absolutePath}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string-array")
        return (0 until nodes.length).associate { i ->
            val el = nodes.item(i) as Element
            val items = el.getElementsByTagName("item")
            el.getAttribute("name") to (0 until items.length).map { items.item(it).textContent.trim() }
        }
    }

    private val en = arrays("values")
    private val tr = arrays("values-tr")

    private val packs = listOf("friends", "best", "couples")

    // Prefix matches, so Turkish suffixes are caught too ("birayı", "içkiler").
    private val banned = Regex(
        "(?U)\\b(drink|drunk|shot|alcohol|booze|beer|wine|vodka|whisk|tequila|cocktail|raki|rakı|sip\\b|chug|" +
            "içki|içer|içe|bira(?!z)|şarap|alkol|sarhoş|kadeh|yudum|votka|viski|kokteyl)",
        RegexOption.IGNORE_CASE,
    )

    @Test
    fun everyPackHasTruthsAndDaresInBothLanguages() {
        for (pack in packs) for (kind in listOf("truth", "dare")) {
            val name = "game_${pack}_$kind"
            val e = en[name].orEmpty()
            val t = tr[name].orEmpty()
            assertTrue("$name EN too small", e.size >= 15)
            assertEquals("$name EN/TR count", e.size, t.size)
            assertEquals("$name has duplicates", e.size, e.toSet().size)
            (e + t).forEach { assertTrue("$name too long: $it", it.isNotEmpty() && it.length <= 200) }
        }
        assertEquals(en.keys, tr.keys)
    }

    @Test
    fun filterCatchesBadWords() {
        listOf("Take a shot", "Bir şarap söyle", "İçki iç", "birayı bitir", "Sarhoş ol", "Drink up").forEach {
            assertTrue("filter missed: $it", banned.containsMatchIn(it))
        }
        listOf("screenshot", "biraz", "şarkı söyle", "içten iltifat").forEach {
            assertTrue("false alarm: $it", !banned.containsMatchIn(it))
        }
    }

    @Test
    fun nothingMentionsAlcohol() {
        (en + tr.mapKeys { "tr:" + it.key }).forEach { (name, items) ->
            items.forEach { assertTrue("$name mentions alcohol: $it", !banned.containsMatchIn(it)) }
        }
    }
}
