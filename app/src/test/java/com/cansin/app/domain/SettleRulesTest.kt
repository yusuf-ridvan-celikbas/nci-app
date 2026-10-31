package com.cansin.app.domain

import com.cansin.app.data.Note
import org.junit.Assert.assertEquals
import org.junit.Test

class SettleRulesTest {
    private fun note(person: String?, amount: Long?) = Note(sessionId = 1, text = "x", person = person, amountMinor = amount, createdAt = 0)

    @Test
    fun groupsByPersonIgnoringCase() {
        val result = SettleRules.byPerson(
            listOf(note("ali", 100), note("Zeynep", 50), note(" Ali ", 25), note(null, 10), note("  ", 5), note("Can", null)),
        )
        assertEquals(listOf(PersonTotal("ali", 125), PersonTotal("Zeynep", 50), PersonTotal(null, 15)), result)
        assertEquals(emptyList<PersonTotal>(), SettleRules.byPerson(listOf(note("Can", null))))
    }

    @Test
    fun splitsSharedHalfAndHalf() {
        assertEquals(150L to 70L, SettleRules.splitShared(TabTotals(red = 100, blue = 20, shared = 100)))
        assertEquals(51L to 50L, SettleRules.splitShared(TabTotals(shared = 101)))
        assertEquals(0L to 0L, SettleRules.splitShared(TabTotals()))
    }

    @Test
    fun evenSplitAlwaysCoversTheBill() {
        assertEquals(3334, SettleRules.eachPays(10_000, 3))
        assertEquals(5000, SettleRules.eachPays(10_000, 2))
        assertEquals(5000, SettleRules.eachPays(10_000, 1))
        assertEquals(500, SettleRules.eachPays(10_000, 99))
        assertEquals(0, SettleRules.eachPays(0, 4))
    }
}
