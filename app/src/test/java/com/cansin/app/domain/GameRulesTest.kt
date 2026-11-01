package com.cansin.app.domain

import com.cansin.app.data.GroupSeat
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameRulesTest {
    @Test
    fun playersAreCleanedAndCapped() {
        assertEquals(listOf("Ali", "Can"), GameRules.cleanPlayers(listOf(" Ali ", "", null, "Can")))
        assertEquals(8, GameRules.cleanPlayers(List(12) { "p$it" }).size)
        assertFalse(GameRules.canStart(listOf("solo")))
        assertTrue(GameRules.canStart(listOf("a", "b")))
    }

    @Test
    fun questionsAreCleaned() {
        assertEquals("Hi there?", GameRules.cleanQuestion("  Hi\u0007 there?\n"))
        assertNull(GameRules.cleanQuestion("   "))
        assertNull(GameRules.cleanQuestion("x".repeat(201)))
        assertEquals(200, GameRules.cleanQuestion("x".repeat(200))?.length)
    }

    @Test
    fun avoidRepeatNeverPicksLast() {
        val random = Random(42)
        repeat(500) {
            val last = it % 3
            assertNotEquals(last, GameRules.pickNext(3, last, avoidRepeat = true, random = random))
        }
        val seen = (1..300).map { GameRules.pickNext(4, 1, true, random) }.toSet()
        assertEquals(setOf(0, 2, 3), seen)
        assertEquals(0, GameRules.pickNext(1, 0, true, random))
    }

    @Test
    fun spinLandsOnTheChosenPlayer() {
        for (count in 2..8) for (index in 0 until count) for (start in listOf(0f, 17f, 725f, -40f)) {
            val target = GameRules.spinTarget(start, index, count)
            assertTrue(target > start + 360f)
            assertEquals("$count/$index/$start", index, GameRules.pointedAt(target, count))
        }
        assertEquals(0f, GameRules.angleFor(0, 4))
        assertEquals(90f, GameRules.angleFor(1, 4))
    }

    @Test
    fun shuffleBagCoversAllBeforeRepeating() {
        val items = (1..10).map { "q$it" }
        val bag = ShuffleBag(items, Random(7))
        val first = List(10) { bag.next() }
        assertEquals(items.toSet(), first.toSet())
        val second = List(10) { bag.next() }
        assertEquals(items.toSet(), second.toSet())
        assertNotEquals(first.last(), second.first())
        assertNull(ShuffleBag(emptyList(), Random(1)).next())
    }

    @Test
    fun linkedSidesFollowTheSession() {
        val group = Session(id = 1, startedAt = 0, mode = SessionMode.GROUP)
        val seats = listOf(GroupSeat(1, Side.GREEN), GroupSeat(1, Side.RED))
        assertEquals(listOf(Side.RED, Side.GREEN), GameRules.linkedSides(group, seats))
        assertEquals(listOf(Side.RED, Side.BLUE), GameRules.linkedSides(group.copy(mode = SessionMode.VERSUS), emptyList()))
        assertTrue(GameRules.linkedSides(Session(startedAt = 0), emptyList()).isEmpty())
        assertTrue(GameRules.linkedSides(null, emptyList()).isEmpty())
    }
}
