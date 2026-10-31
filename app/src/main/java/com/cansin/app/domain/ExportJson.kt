package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.FriendProfile
import com.cansin.app.data.FriendResult
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
)

object ExportJson {
    const val FORMAT_VERSION = 1

    fun build(data: ExportData, includeProfile: Boolean, exportedAt: Long): String {
        val beers = data.beers.groupBy { it.sessionId }
        val water = data.water.groupBy { it.sessionId }
        val notes = data.notes.groupBy { it.sessionId }
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
                    "beers" to beers[s.id].orEmpty().map {
                        linkedMapOf("side" to it.side.name, "sizeMl" to it.sizeMl, "abv" to it.abv, "at" to it.at, "finishedAt" to it.finishedAt)
                    },
                    "water" to water[s.id].orEmpty().map { linkedMapOf("at" to it.at) },
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
