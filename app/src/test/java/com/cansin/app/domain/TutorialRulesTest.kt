package com.cansin.app.domain

import com.cansin.app.domain.TutorialTopic.FRIENDS
import com.cansin.app.domain.TutorialTopic.GENERAL
import com.cansin.app.domain.TutorialTopic.TAB
import com.cansin.app.domain.TutorialTopic.TONIGHT
import com.cansin.app.domain.TutorialTopic.WIDGET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialRulesTest {
    private fun auto(
        seen: Set<TutorialTopic>,
        section: TutorialTopic?,
        age: Boolean = true,
        locked: Boolean = false,
        showing: Boolean = false,
    ) = TutorialRules.autoShow(seen, section, age, locked, showing)

    @Test
    fun generalComesFirstWherever() {
        assertEquals(GENERAL, auto(emptySet(), TAB))
        assertEquals(GENERAL, auto(setOf(TAB), null))
    }

    @Test
    fun sectionOnFirstVisitAfterGeneral() {
        assertEquals(TONIGHT, auto(setOf(GENERAL), TONIGHT))
        assertEquals(FRIENDS, auto(setOf(GENERAL, TONIGHT), FRIENDS))
        assertNull(auto(setOf(GENERAL, TAB), TAB))
        assertNull(auto(setOf(GENERAL), null))
    }

    @Test
    fun widgetNeverAutomatic() {
        assertNull(auto(setOf(GENERAL), WIDGET))
    }

    @Test
    fun nothingBehindGateLockOrAnotherGuide() {
        assertNull(auto(emptySet(), TONIGHT, age = false))
        assertNull(auto(emptySet(), TONIGHT, locked = true))
        assertNull(auto(emptySet(), TONIGHT, showing = true))
    }

    @Test
    fun tourKeepsOrderAndOnlyPresentStepsOfTopic() {
        val present = setOf(TourStep.TONIGHT_END, TourStep.TONIGHT_PLUS, TourStep.TAB_SETTLE, TourStep.FRIENDS_END)
        assertEquals(listOf(TourStep.TONIGHT_PLUS, TourStep.TONIGHT_END), TutorialRules.tour(TONIGHT, present))
        assertEquals(emptyList<TourStep>(), TutorialRules.tour(TONIGHT, emptySet()))
    }

    @Test
    fun tonightTourOnlySoloFriendsTourOnlyShared() {
        assertTrue(TutorialRules.available(TONIGHT, sharedOpen = false))
        assertFalse(TutorialRules.available(TONIGHT, sharedOpen = true))
        assertTrue(TutorialRules.available(FRIENDS, sharedOpen = true))
        assertFalse(TutorialRules.available(FRIENDS, sharedOpen = false))
        assertTrue(TutorialRules.available(TAB, sharedOpen = true))
        assertTrue(TutorialRules.available(TAB, sharedOpen = false))
    }

    @Test
    fun cardsOnlyForGeneralAndWidget() {
        assertEquals(setOf(GENERAL, WIDGET), TutorialTopic.entries.filter(TutorialRules::isCards).toSet())
    }

    @Test
    fun everyTourTopicHasSteps() {
        TutorialTopic.entries.filterNot(TutorialRules::isCards).forEach { topic ->
            assertTrue(topic.name, TourStep.entries.any { it.topic == topic })
        }
    }

    @Test
    fun codecRoundTripsAndDropsUnknown() {
        val seen = setOf(GENERAL, WIDGET)
        assertEquals(seen, TutorialRules.decode(TutorialRules.encode(seen)))
        assertEquals(setOf(TAB), TutorialRules.decode(setOf("TAB", "OLD_TOPIC", "")))
        assertEquals(emptySet<TutorialTopic>(), TutorialRules.decode(null))
    }
}
