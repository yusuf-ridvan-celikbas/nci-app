package com.cansin.app.domain

import com.cansin.app.data.CustomQuestion
import com.cansin.app.data.GroupSeat
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side
import kotlin.random.Random

/** Built-in question packs (texts live in res/values/game_questions.xml and values-tr). */
enum class GamePack { FRIENDS, BEST_FRIENDS, COUPLES }

/**
 * P26 Truth or Dare with a spinning glass. Never about alcohol: no drinking dares, no penalties, skip is free.
 * Glass angle: degrees clockwise from 12 o'clock; player i sits at 360·i/n.
 */
object GameRules {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = GroupRules.MAX_SEATS
    const val DEFAULT_TURNS = 4

    fun cleanPlayers(names: List<String?>): List<String> =
        names.mapNotNull { VersusRules.cleanName(it) }.take(MAX_PLAYERS)

    fun canStart(players: List<String>): Boolean = players.size in MIN_PLAYERS..MAX_PLAYERS

    /** Trimmed, no control characters, 1..200 chars. */
    fun cleanQuestion(raw: String?): String? {
        val t = raw?.filterNot { Character.isISOControl(it) }?.trim() ?: return null
        return t.takeIf { it.isNotEmpty() && it.length <= CustomQuestion.MAX_TEXT }
    }

    /** Random next player; with [avoidRepeat] never the same as [last] (needs ≥2 players). */
    fun pickNext(count: Int, last: Int?, avoidRepeat: Boolean, random: Random): Int {
        require(count >= 1)
        if (!avoidRepeat || last == null || count < 2 || last !in 0 until count) return random.nextInt(count)
        val pick = random.nextInt(count - 1)
        return if (pick >= last) pick + 1 else pick
    }

    fun angleFor(index: Int, count: Int): Float = 360f * index / count

    /** A target past [current] by [turns] full turns whose rim ends pointing at [index] (optionally [tilt]ed). */
    fun spinTarget(current: Float, index: Int, count: Int, turns: Int = DEFAULT_TURNS, tilt: Float = 0f): Float {
        val base = Math.floorDiv(current.toInt(), 360) * 360f
        return base + turns * 360f + angleFor(index, count) + tilt
    }

    /** Two-ended glass: the foot (opposite the rim) points at who asks. Nearest seat(s) to the opposite side, never the answerer. */
    fun askerCandidates(answerer: Int, count: Int): List<Int> = when {
        count < 2 -> emptyList()
        count % 2 == 0 -> listOf((answerer + count / 2) % count)
        else -> listOf((answerer + (count - 1) / 2) % count, (answerer + (count + 1) / 2) % count)
    }

    /** For odd counts, tilt the glass a little so the foot visibly leans toward the chosen asker (rim stays on the answerer). */
    fun askerTilt(answerer: Int, asker: Int, count: Int): Float {
        if (count % 2 == 0 || count < 3) return 0f
        val step = TILT_SHARE * 360f / count
        return if (asker == (answerer + (count - 1) / 2) % count) -step else step
    }

    private const val TILT_SHARE = 0.2f

    /** Which player the glass points at for [angle] (nearest seat). */
    fun pointedAt(angle: Float, count: Int): Int {
        val a = ((angle % 360f) + 360f) % 360f
        return Math.floorMod(Math.round(a * count / 360f), count)
    }

    /** Sides to offer as players from an open Versus/Together session, in seat order. */
    fun linkedSides(session: Session?, seats: List<GroupSeat>): List<Side> = when (session?.mode) {
        SessionMode.VERSUS -> listOf(Side.RED, Side.BLUE)
        SessionMode.GROUP -> GroupRules.ordered(seats).map { it.side }
        else -> emptyList()
    }
}

/** Draws every item once (random order) before any repeats; reshuffles when empty. */
class ShuffleBag(private val items: List<String>, private val random: Random) {
    private val left = ArrayDeque<String>()
    private var lastDrawn: String? = null

    val source: List<String> get() = items

    fun next(): String? {
        if (items.isEmpty()) return null
        if (left.isEmpty()) {
            val fresh = items.shuffled(random).toMutableList()
            // Avoid the same item twice in a row across a reshuffle.
            if (fresh.size > 1 && fresh.first() == lastDrawn) fresh.add(fresh.removeAt(0))
            left.addAll(fresh)
        }
        return left.removeFirst().also { lastDrawn = it }
    }
}
