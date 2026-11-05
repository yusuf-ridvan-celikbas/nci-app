package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.DrinkKind
import com.cansin.app.data.FriendProfile
import com.cansin.app.data.FriendResult
import com.cansin.app.data.GroupSeat
import com.cansin.app.data.CustomQuestion
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.WaterEvent

data class ExportData(
    val sessions: List<Session>,
    val beers: List<BeerEvent>,
    val water: List<WaterEvent>,
    val notes: List<Note>,
    val friends: List<FriendProfile>,
    val profile: BodyProfile?,
    val friendResults: List<FriendResult> = emptyList(),
    val seats: List<GroupSeat> = emptyList(),
    val customQuestions: List<CustomQuestion> = emptyList(),
    val currency: Currency = Currency.NONE,
    /** P28 price per drink kind (minor units). */
    val prices: Map<PriceSlot, Long> = emptyMap(),
)

object ExportJson {
    const val FORMAT_VERSION = 1

    fun build(data: ExportData, includeProfile: Boolean, exportedAt: Long): String {
        val beers = data.beers.groupBy { it.sessionId }
        val water = data.water.groupBy { it.sessionId }
        val notes = data.notes.groupBy { it.sessionId }
        val seats = data.seats.groupBy { it.sessionId }
        val root = linkedMapOf<String, Any?>(
            "format" to "cansin-export",
            "version" to FORMAT_VERSION,
            "exportedAt" to exportedAt,
            "sessions" to data.sessions.sortedBy { it.startedAt }.map { s ->
                linkedMapOf(
                    "id" to s.id,
                    "startedAt" to s.startedAt,
                    "endedAt" to s.endedAt,
                    "mode" to s.mode.name,
                    "redName" to s.redName,
                    "blueName" to s.blueName,
                    "redFriendId" to s.redFriendId,
                    "blueFriendId" to s.blueFriendId,
                    "meSide" to s.meSide?.name,
                    // P27: only for compressed nights (their event arrays are empty).
                    "totals" to s.totals?.let { t ->
                        linkedMapOf<String, Any?>(
                            "compressedAt" to t.compressedAt,
                            "drinks" to t.drinks,
                            "mine" to t.mine,
                            "water" to t.water,
                            "myWater" to t.myWater,
                            "notes" to t.notes,
                            "spentMinor" to t.spentMinor,
                            "paceMin" to t.paceMin,
                            "peak" to t.peak,
                            "sides" to t.sides,
                        )
                    },
                    "seats" to seats[s.id]?.let { GroupRules.ordered(it).map { seat -> linkedMapOf("side" to seat.side.name, "name" to seat.name) } },
                    "beers" to beers[s.id].orEmpty().map {
                        linkedMapOf<String, Any?>("side" to it.side.name, "sizeMl" to it.sizeMl, "abv" to it.abv, "at" to it.at, "finishedAt" to it.finishedAt)
                            .apply {
                                if (it.kind != DrinkKind.BEER) put("kind", it.kind.name)
                                it.priceMinor?.let { p -> put("priceMinor", p) }
                                it.brand?.let { b -> put("brand", b) }
                            }
                    },
                    "water" to water[s.id].orEmpty().map { w ->
                        linkedMapOf<String, Any?>("at" to w.at).apply { w.side?.let { put("side", it.name) } }
                    },
                    "notes" to notes[s.id].orEmpty().map {
                        linkedMapOf(
                            "text" to it.text,
                            "person" to it.person,
                            "amountMinor" to it.amountMinor,
                            "side" to it.side?.name,
                            "createdAt" to it.createdAt,
                            "editedTime" to it.editedTime,
                        )
                    },
                )
            },
            "friends" to data.friends.map { linkedMapOf("id" to it.id, "name" to it.name) },
            "friendResults" to data.friendResults.map {
                linkedMapOf("name" to it.name, "day" to it.day, "beers" to it.beers, "durationMin" to it.durationMin, "water" to it.water)
            },
        )
        if (data.currency != Currency.NONE) root["currency"] = data.currency.name
        if (data.prices.isNotEmpty()) root["drinkPrices"] = data.prices.entries.sortedBy { it.key.ordinal }.associateTo(linkedMapOf()) { it.key.name to it.value }
        if (data.customQuestions.isNotEmpty()) {
            root["customQuestions"] = data.customQuestions.map { linkedMapOf("kind" to it.kind.name, "text" to it.text, "createdAt" to it.createdAt) }
        }
        val profile = data.profile
        if (includeProfile && profile != null) {
            root["bodyProfile"] = linkedMapOf(
                "sex" to profile.sex.name,
                "age" to profile.age,
                "heightCm" to profile.heightCm,
                "weightKg" to profile.weightKg,
                "license" to profile.license.name,
            )
        }
        return StringBuilder().also { write(it, root) }.toString()
    }

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> string(out, value)
            is Boolean, is Int, is Long -> out.append(value.toString())
            is Double -> out.append(if (value.isFinite()) value.toString() else "null")
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) out.append(',')
                    string(out, k.toString())
                    out.append(':')
                    write(out, v)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) out.append(',')
                    write(out, v)
                }
                out.append(']')
            }
            else -> string(out, value.toString())
        }
    }

    private fun string(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' || c == ' ' || c == ' ' -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
    }
}
