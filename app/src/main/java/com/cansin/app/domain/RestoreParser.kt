package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.FriendResult
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.Side
import com.cansin.app.data.WaterEvent
import java.time.LocalDate

/** One session with its events; ids are 0 and get assigned on insert. */
data class RestoreSession(val session: Session, val beers: List<BeerEvent>, val water: List<WaterEvent>, val notes: List<Note>)

/** Friend from the file; [fileId] links sessions to it before new ids exist. */
data class RestoreFriend(val fileId: Long, val name: String)

data class RestoreData(
    val sessions: List<RestoreSession>,
    val friends: List<RestoreFriend>,
    val friendResults: List<FriendResult>,
    val currency: Currency?,
    val profile: BodyProfile?,
) {
    val beerCount get() = sessions.sumOf { it.beers.size }
}

enum class RestoreError { NOT_OURS, NEWER_VERSION, INVALID, TOO_LARGE }

sealed interface RestoreResult {
    data class Ok(val data: RestoreData) : RestoreResult
    data class Fail(val error: RestoreError) : RestoreResult
}

/** Reads our own export (format "cansin-export" v1). Strict: one bad item rejects the whole file. */
object RestoreParser {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_SESSIONS = 10_000
    const val MAX_EVENTS = 200_000
    const val MAX_FRIENDS = 1_000
    const val MAX_FRIEND_RESULTS = 10_000
    private const val FUTURE_SLACK_MS = 24 * 60 * 60 * 1000L

    private class Bad(val error: RestoreError = RestoreError.INVALID) : Exception()

    fun parse(text: String, now: Long): RestoreResult {
        if (text.length > MAX_BYTES) return RestoreResult.Fail(RestoreError.TOO_LARGE)
        return try {
            val root = (MiniJson.parse(text) as? Map<*, *>) ?: throw Bad(RestoreError.NOT_OURS)
            if (root["format"] != "cansin-export") throw Bad(RestoreError.NOT_OURS)
            val version = root["version"] as? Long ?: throw Bad()
            if (version > ExportJson.FORMAT_VERSION) throw Bad(RestoreError.NEWER_VERSION)
            if (version < 1) throw Bad()
            RestoreResult.Ok(Reader(now).read(root))
        } catch (e: Bad) {
            RestoreResult.Fail(e.error)
        } catch (_: MiniJson.Error) {
            RestoreResult.Fail(RestoreError.INVALID)
        }
    }

    private class Reader(val now: Long) {
        var events = 0

        fun read(root: Map<*, *>): RestoreData {
            val friends = list(root["friends"], MAX_FRIENDS, optional = true).map { friend(obj(it)) }
            if (friends.map { it.fileId }.toSet().size != friends.size) throw Bad()
            val friendIds = friends.map { it.fileId }.toSet()
            val sessions = list(root["sessions"], MAX_SESSIONS, optional = false).map { session(obj(it), friendIds) }
            if (sessions.count { it.session.endedAt == null } > 1) throw Bad()
            val results = list(root["friendResults"], MAX_FRIEND_RESULTS, optional = true).map { friendResult(obj(it)) }
                .distinctBy { listOf(it.name, it.day, it.beers, it.durationMin, it.water) }
            val currency = root["currency"]?.let { c -> Currency.entries.firstOrNull { it.name == c && it != Currency.NONE } ?: throw Bad() }
            val profile = root["bodyProfile"]?.let { profile(obj(it)) }
            return RestoreData(sessions, friends, results, currency, profile)
        }

        fun friend(o: Map<*, *>) = RestoreFriend(long(o["id"]), VersusRules.cleanName(str(o["name"])) ?: throw Bad())

        fun session(o: Map<*, *>, friendIds: Set<Long>): RestoreSession {
            val mode = enum<SessionMode>(o["mode"])
            val start = time(o["startedAt"])
            val end = o["endedAt"]?.let { time(it) }?.also { if (it < start) throw Bad() }
            val versus = mode == SessionMode.VERSUS
            fun friendRef(v: Any?): Long? = v?.let { long(it) }?.also { if (!versus || it !in friendIds) throw Bad() }
            fun name(v: Any?): String? = v?.let { VersusRules.cleanName(str(it)) ?: throw Bad() }?.also { if (!versus) throw Bad() }
            val me = o["meSide"]?.let { enum<Side>(it) }?.also { if (!versus || it == Side.SOLO) throw Bad() }
            val session = Session(
                startedAt = start, endedAt = end, mode = mode,
                redName = name(o["redName"]), blueName = name(o["blueName"]),
                redFriendId = friendRef(o["redFriendId"]), blueFriendId = friendRef(o["blueFriendId"]),
                meSide = me,
            )
            val beers = events(o["beers"]).map { b ->
                val side = enum<Side>(b["side"]).also { if (!VersusRules.fits(it, mode)) throw Bad() }
                val size = int(b["sizeMl"]).also { if (it !in BeerRules.MIN_ML..BeerRules.MAX_ML) throw Bad() }
                val abv = double(b["abv"]).also { if (it !in BeerRules.MIN_ABV..BeerRules.MAX_ABV) throw Bad() }
                BeerEvent(sessionId = 0, side = side, sizeMl = size, abv = abv, at = time(b["at"]), finishedAt = b["finishedAt"]?.let { time(it) })
            }
            val water = events(o["water"]).map { WaterEvent(sessionId = 0, at = time(it["at"])) }
            val notes = events(o["notes"]).map { n ->
                val raw = str(n["text"])
                if (raw.length > Note.MAX_TEXT) throw Bad()
                val person = n["person"]?.let { p -> str(p).also { if (it.length > Note.MAX_PERSON) throw Bad() } }
                val amount = n["amountMinor"]?.let { long(it) }?.also { if (!NoteRules.isValidAmount(it)) throw Bad() }
                val side = n["side"]?.let { enum<Side>(it) }?.also { if (VersusRules.noteSide(it, mode) != it) throw Bad() }
                Note(
                    sessionId = 0,
                    text = NoteRules.cleanText(raw) ?: throw Bad(),
                    person = NoteRules.cleanPerson(person),
                    amountMinor = amount,
                    createdAt = time(n["createdAt"]),
                    editedTime = n["editedTime"]?.let { time(it) },
                    side = side,
                )
            }
            return RestoreSession(session, beers, water, notes)
        }

        fun friendResult(o: Map<*, *>): FriendResult {
            val day = runCatching { LocalDate.parse(str(o["day"])) }.getOrNull() ?: throw Bad()
            fun count(v: Any?, max: Int) = int(v).also { if (it !in 0..max) throw Bad() }
            return FriendResult(
                name = ResultCards.cleanName(str(o["name"])) ?: throw Bad(),
                day = day.toString(),
                beers = count(o["beers"], ResultCards.MAX_BEERS),
                durationMin = count(o["durationMin"], ResultCards.MAX_DURATION_MIN),
                water = count(o["water"], ResultCards.MAX_WATER),
                importedAt = now,
            )
        }

        fun profile(o: Map<*, *>): BodyProfile =
            BodyProfile(enum(o["sex"]), int(o["age"]), int(o["heightCm"]), int(o["weightKg"]), enum(o["license"]))
                .takeIf { it.isValid } ?: throw Bad()

        fun events(v: Any?): List<Map<*, *>> {
            val items = list(v, MAX_EVENTS, optional = true)
            events += items.size
            if (events > MAX_EVENTS) throw Bad(RestoreError.TOO_LARGE)
            return items.map { obj(it) }
        }

        fun list(v: Any?, max: Int, optional: Boolean): List<Any?> {
            if (v == null && optional) return emptyList()
            val l = v as? List<*> ?: throw Bad()
            if (l.size > max) throw Bad(RestoreError.TOO_LARGE)
            return l
        }

        fun obj(v: Any?): Map<*, *> = v as? Map<*, *> ?: throw Bad()
        fun str(v: Any?): String = v as? String ?: throw Bad()
        fun long(v: Any?): Long = v as? Long ?: throw Bad()
        fun int(v: Any?): Int = long(v).takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: throw Bad()
        fun double(v: Any?): Double = when (v) {
            is Long -> v.toDouble()
            is Double -> v
            else -> throw Bad()
        }
        fun time(v: Any?): Long = long(v).also { if (it < 0 || it > now + FUTURE_SLACK_MS) throw Bad() }

        inline fun <reified E : Enum<E>> enum(v: Any?): E = enumValues<E>().firstOrNull { it.name == v } ?: throw Bad()
    }
}
