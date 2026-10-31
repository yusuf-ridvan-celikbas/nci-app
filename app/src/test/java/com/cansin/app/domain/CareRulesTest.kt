package com.cansin.app.domain

import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CareRulesTest {
    private val now = 10_000_000L
    private val base = CareInput(enabled = true, sessionActive = true, now = now, hour = 20, lateHour = 1, myBeers = 1)

    private fun pick(input: CareInput, dismissed: CareDismissals = CareDismissals()) = CareRules.pick(input, dismissed)

    @Test
    fun lateWindowWrapsMidnight() {
        assertTrue(CareRules.isLate(23, 22))
        assertTrue(CareRules.isLate(3, 22))
        assertFalse(CareRules.isLate(6, 22))
        assertFalse(CareRules.isLate(21, 22))
        assertTrue(CareRules.isLate(1, 1))
        assertTrue(CareRules.isLate(5, 0))
        assertFalse(CareRules.isLate(23, 0))
        assertFalse(CareRules.isLate(0, 1))
    }

    @Test
    fun picksOneMessageByPriority() {
        assertEquals(CareKind.LATE, pick(base.copy(hour = 2, myBeers = 7)))
        assertEquals(CareKind.SLOW_DOWN, pick(base.copy(myBeers = 5)))
        assertNull(pick(base.copy(myBeers = 4)))
        assertNull(pick(base.copy(hour = 2, sessionActive = false)))
        assertNull(pick(base.copy(hour = 2, myBeers = 9, enabled = false)))
        assertEquals(CareKind.BEER_LIMIT, pick(base.copy(myBeers = 3, beerLimit = 3, enabled = false)))
        assertEquals(CareKind.BEER_LIMIT, pick(base.copy(hour = 2, myBeers = 3, beerLimit = 3, budgetMinor = 100, mySpendMinor = 500)))
        assertEquals(CareKind.BUDGET, pick(base.copy(hour = 2, myBeers = 2, beerLimit = 3, budgetMinor = 100, mySpendMinor = 100)))
        assertNull(pick(base.copy(myBeers = 2, beerLimit = 3, budgetMinor = 100, mySpendMinor = 99)))
    }

    @Test
    fun dismissalsSnoozeThenReturn() {
        assertEquals(CareKind.SLOW_DOWN, pick(base.copy(hour = 2, myBeers = 6), CareDismissals(lateAt = now - 1_000)))
        assertEquals(CareKind.LATE, pick(base.copy(hour = 2), CareDismissals(lateAt = now - CareRules.LATE_SNOOZE_MS)))
        assertNull(pick(base.copy(myBeers = 6), CareDismissals(slowDownAtBeers = 6)))
        assertEquals(CareKind.SLOW_DOWN, pick(base.copy(myBeers = 7), CareDismissals(slowDownAtBeers = 6)))
        assertNull(pick(base.copy(myBeers = 3, beerLimit = 3), CareDismissals(limitAtBeers = 3)))
        assertEquals(CareKind.BEER_LIMIT, pick(base.copy(myBeers = 4, beerLimit = 3), CareDismissals(limitAtBeers = 3)))
        assertNull(pick(base.copy(budgetMinor = 100, mySpendMinor = 150), CareDismissals(budgetAtSpend = 150)))
        assertEquals(CareKind.BUDGET, pick(base.copy(budgetMinor = 100, mySpendMinor = 200), CareDismissals(budgetAtSpend = 150)))
    }

    @Test
    fun mySpendFollowsMySide() {
        val notes = listOf(
            Note(sessionId = 1, text = "a", amountMinor = 100, side = Side.RED, createdAt = 0),
            Note(sessionId = 1, text = "b", amountMinor = 40, side = Side.BLUE, createdAt = 0),
            Note(sessionId = 1, text = "c", amountMinor = 10, createdAt = 0),
            Note(sessionId = 1, text = "d", createdAt = 0),
        )
        assertEquals(150, CareRules.mySpend(Session(id = 1, startedAt = 0), notes))
        val versus = Session(id = 1, startedAt = 0, mode = SessionMode.VERSUS)
        assertEquals(110, CareRules.mySpend(versus.copy(meSide = Side.RED), notes))
        assertEquals(50, CareRules.mySpend(versus.copy(meSide = Side.BLUE), notes))
        assertEquals(150, CareRules.mySpend(versus, notes))
    }

    @Test
    fun cleansInputs() {
        assertEquals("Kadıköy", CareRules.cleanHome("  Kadıköy\n "))
        assertNull(CareRules.cleanHome("   "))
        assertEquals(CareRules.MAX_HOME_LENGTH, CareRules.cleanHome("x".repeat(500))!!.length)
        assertEquals(CareRules.DEFAULT_LATE_HOUR, CareRules.clampLateHour(12))
        assertEquals(23, CareRules.clampLateHour(23))
        assertNull(CareRules.cleanBeerLimit(0))
        assertNull(CareRules.cleanBeerLimit(21))
        assertEquals(4, CareRules.cleanBeerLimit(4))
        assertNull(CareRules.cleanBudget(0))
        assertNull(CareRules.cleanBudget(NoteRules.MAX_AMOUNT_MINOR + 1))
    }
}
