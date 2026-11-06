package com.cansin.app.domain

/** P37 guides. GENERAL and WIDGET are swipe cards; the others are spotlight tours over the real screen. */
enum class TutorialTopic { GENERAL, TONIGHT, TAB, GAME, HISTORY, FRIENDS, WIDGET }

/** One spotlight stop. Order here = order in the tour; a stop is skipped when its button isn't on screen. */
enum class TourStep(val topic: TutorialTopic) {
    TONIGHT_PLUS(TutorialTopic.TONIGHT),
    TONIGHT_DRINKS(TutorialTopic.TONIGHT),
    TONIGHT_TIMER(TutorialTopic.TONIGHT),
    TONIGHT_WATER(TutorialTopic.TONIGHT),
    TONIGHT_PROMILLE(TutorialTopic.TONIGHT),
    TONIGHT_FRIENDS(TutorialTopic.TONIGHT),
    TONIGHT_END(TutorialTopic.TONIGHT),
    TONIGHT_NAV(TutorialTopic.TONIGHT),
    TAB_DRINKS(TutorialTopic.TAB),
    TAB_ADD(TutorialTopic.TAB),
    TAB_TOTAL(TutorialTopic.TAB),
    TAB_SETTLE(TutorialTopic.TAB),
    GAME_LINKED(TutorialTopic.GAME),
    GAME_PLAYERS(TutorialTopic.GAME),
    GAME_PACKS(TutorialTopic.GAME),
    GAME_MINE(TutorialTopic.GAME),
    GAME_START(TutorialTopic.GAME),
    GAME_GLASS(TutorialTopic.GAME),
    GAME_ACTIONS(TutorialTopic.GAME),
    GAME_CHANGE(TutorialTopic.GAME),
    HISTORY_TABS(TutorialTopic.HISTORY),
    HISTORY_FILTER(TutorialTopic.HISTORY),
    HISTORY_LIST(TutorialTopic.HISTORY),
    HISTORY_SELECT(TutorialTopic.HISTORY),
    VS_PAGES(TutorialTopic.FRIENDS),
    VS_SIDES(TutorialTopic.FRIENDS),
    GROUP_SEATS(TutorialTopic.FRIENDS),
    GROUP_LAYOUT(TutorialTopic.FRIENDS),
    GROUP_GAME(TutorialTopic.FRIENDS),
    FRIENDS_EDIT(TutorialTopic.FRIENDS),
    FRIENDS_PIN(TutorialTopic.FRIENDS),
    FRIENDS_END(TutorialTopic.FRIENDS),
}

object TutorialRules {
    /** Swipe cards (no screen to point at) vs a spotlight tour. */
    fun isCards(topic: TutorialTopic): Boolean = topic == TutorialTopic.GENERAL || topic == TutorialTopic.WIDGET

    /** Tonight's tour needs the solo counter; the Versus/Together tour needs one of those open. */
    fun available(topic: TutorialTopic, sharedOpen: Boolean): Boolean = when (topic) {
        TutorialTopic.TONIGHT -> !sharedOpen
        TutorialTopic.FRIENDS -> sharedOpen
        else -> true
    }

    /** The tour's stops: this topic's steps that are on screen right now, in order. */
    fun tour(topic: TutorialTopic, present: Set<TourStep>): List<TourStep> =
        TourStep.entries.filter { it.topic == topic && it in present }

    /**
     * Which guide opens by itself: GENERAL once after the 18+ gate, then a section's guide on its first visit.
     * Never behind the PIN lock or the age gate, and never on top of another guide.
     */
    fun autoShow(
        seen: Set<TutorialTopic>,
        section: TutorialTopic?,
        ageConfirmed: Boolean,
        locked: Boolean,
        showing: Boolean,
    ): TutorialTopic? = when {
        !ageConfirmed || locked || showing -> null
        TutorialTopic.GENERAL !in seen -> TutorialTopic.GENERAL
        section == null || section == TutorialTopic.WIDGET || section in seen -> null
        else -> section
    }

    /** Stored as names; unknown names (from a newer or older version) are dropped. */
    fun decode(raw: Set<String>?): Set<TutorialTopic> =
        raw.orEmpty().mapNotNull { name -> TutorialTopic.entries.firstOrNull { it.name == name } }.toSet()

    fun encode(seen: Set<TutorialTopic>): Set<String> = seen.mapTo(mutableSetOf()) { it.name }
}
