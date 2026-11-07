package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.DrinkKind
import com.cansin.app.data.FriendResult
import com.cansin.app.data.GroupSeat
import com.cansin.app.data.CustomQuestion
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.SessionTotals
import com.cansin.app.data.Side
import com.cansin.app.data.WaterEvent
import java.time.LocalDate

/** One session with its events; ids are 0 and get assigned on insert. */
data class RestoreSession(
    val session: Session,
    val beers: List<BeerEvent>,
    val water: List<WaterEvent>,
    val notes: List<Note>,
    val seats: List<GroupSeat> = emptyList(),
)

/** Friend from the file; [fileId] links sessions to it before new ids exist. */
data class RestoreFriend(val fileId: Long, val name: String)

data class RestoreData(
    val sessions: List<RestoreSession>,
    val friends: List<RestoreFriend>,
    val friendResults: List<FriendResult>,
    val currency: Currency?,
    val profile: BodyProfile?,
    val customQuestions: List<CustomQuestion> = emptyList(),
    /** P28: null = not in the file (keep current prices). */
    val prices: Map<PriceSlot, Long>? = null,
) {
    val beerCount get() = sessions.sumOf { it.beers.size }
}

enum class RestoreError { NOT_OURS, NEWER_VERSION, INVALID, TOO_LARGE }

sealed interface RestoreResult {
    data class Ok(val data: RestoreData) : RestoreResult
    data class Fail(val error: RestoreError) : RestoreResult
}

/** Reads our own export (format "salgam-backup" v1; "cansin-export" from before P40 too). Strict: one bad item rejects the whole file. */
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
            if (root["format"] !in ExportJson.FORMATS) throw Bad(RestoreError.NOT_OURS)
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
            val questions = list(root["customQuestions"], CustomQuestion.MAX_COUNT, optional = true).map { question(obj(it)) }
            val prices = root["drinkPrices"]?.let { v ->
                obj(v).entries.associate { (k, p) ->
                    enum<PriceSlot>(k) to long(p).also { if (it <= 0 || !NoteRules.isValidAmount(it)) throw Bad() }
                }
            }
            return RestoreData(sessions, friends, results, currency, profile, questions, prices)
        }

        fun question(o: Map<*, *>): CustomQuestion {
            val raw = str(o["text"])
            if (raw.length > CustomQuestion.MAX_TEXT) throw Bad()
            return CustomQuestion(kind = enum(o["kind"]), text = GameRules.cleanQuestion(raw) ?: throw Bad(), createdAt = time(o["createdAt"]))
        }

        fun friend(o: Map<*, *>) = RestoreFriend(long(o["id"]), VersusRules.cleanName(str(o["name"])) ?: throw Bad())

        fun session(o: Map<*, *>, friendIds: Set<Long>): RestoreSession {
            val mode = enum<SessionMode>(o["mode"])
            val start = time(o["startedAt"])
            val end = o["endedAt"]?.let { time(it) }?.also { if (it < start) throw Bad() }
            val versus = mode == SessionMode.VERSUS
            fun friendRef(v: Any?): Long? = v?.let { long(it) }?.also { if (!versus || it !in friendIds) throw Bad() }
            fun name(v: Any?): String? = v?.let { VersusRules.cleanName(str(it)) ?: throw Bad() }?.also { if (!versus) throw Bad() }
            val group = mode == SessionMode.GROUP
            val seats = if (group) groupSeats(o["seats"]) else emptyList<GroupSeat>().also { if (o["seats"] != null) throw Bad() }
            val seatSides = seats.map { it.side }.toSet()
            fun onSeat(side: Side) = !group || side in seatSides
            val me = o["meSide"]?.let { enum<Side>(it) }?.also { if (!VersusRules.hasSides(mode) || !VersusRules.fits(it, mode) || !onSeat(it)) throw Bad() }
            val session = Session(
                startedAt = start, endedAt = end, mode = mode,
                redName = name(o["redName"]), blueName = name(o["blueName"]),
                redFriendId = friendRef(o["redFriendId"]), blueFriendId = friendRef(o["blueFriendId"]),
                meSide = me,
            )
            val beers = events(o["beers"]).map { b ->
                val side = enum<Side>(b["side"]).also { if (!VersusRules.fits(it, mode) || !onSeat(it)) throw Bad() }
                val size = int(b["sizeMl"]).also { if (it !in BeerRules.MIN_ML..BeerRules.MAX_ML) throw Bad() }
                val abv = double(b["abv"]).also { if (it !in BeerRules.MIN_ABV..BeerRules.MAX_ABV) throw Bad() }
                val kind = b["kind"]?.let { enum<DrinkKind>(it) } ?: DrinkKind.BEER
                val price = b["priceMinor"]?.let { long(it) }?.also { if (it <= 0 || !NoteRules.isValidAmount(it)) throw Bad() }
                BeerEvent(sessionId = 0, side = side, sizeMl = size, abv = abv, at = time(b["at"]), finishedAt = b["finishedAt"]?.let { time(it) }, kind = kind, priceMinor = price, serving = b["serving"]?.let { enum<com.cansin.app.data.Serving>(it) }?.also { if (kind != DrinkKind.BEER) throw Bad() })
            }
            val water = events(o["water"]).map { w ->
                val side = w["side"]?.let { enum<Side>(it) }
                if (group != (side != null) || (side != null && side !in seatSides)) throw Bad()
                val price = w["priceMinor"]?.let { long(it) }?.also { if (it <= 0 || !NoteRules.isValidAmount(it)) throw Bad() }
                val size = w["sizeMl"]?.let { int(it) }?.also { if (it !in WaterRules.SIZES_ML) throw Bad() } ?: WaterRules.DEFAULT_ML
                WaterEvent(sessionId = 0, at = time(w["at"]), side = side, priceMinor = price, sizeMl = size)
            }
            val notes = events(o["notes"]).map { n ->
                val raw = str(n["text"])
                if (raw.length > Note.MAX_TEXT) throw Bad()
                val person = n["person"]?.let { p -> str(p).also { if (it.length > Note.MAX_PERSON) throw Bad() } }
                val amount = n["amountMinor"]?.let { long(it) }?.also { if (!NoteRules.isValidAmount(it)) throw Bad() }
                val side = n["side"]?.let { enum<Side>(it) }?.also { if (VersusRules.noteSide(it, mode) != it || !onSeat(it)) throw Bad() }
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
            val totals = o["totals"]?.let { totals(obj(it), mode, end) }
            if (totals != null && (beers.isNotEmpty() || water.isNotEmpty() || notes.isNotEmpty())) throw Bad()
            return RestoreSession(session.copy(totals = totals), beers, water, notes, seats)
        }

        /** P27 compressed night: strict ranges; only for ended nights; sides must fit the mode. */
        fun totals(t: Map<*, *>, mode: SessionMode, end: Long?): SessionTotals {
            if (end == null) throw Bad()
            fun optInt(v: Any?): Int? = v?.let { int(it) }
            fun optDouble(v: Any?): Double? = v?.let { double(it) }
            val sides = t["sides"]?.let { str(it) }
            val totals = SessionTotals(
                compressedAt = time(t["compressedAt"]),
                drinks = int(t["drinks"]),
                mine = optInt(t["mine"]),
                water = int(t["water"]),
                myWater = optInt(t["myWater"]),
                notes = int(t["notes"]),
                spentMinor = long(t["spentMinor"]),
                paceMin = optDouble(t["paceMin"]),
                peak = optDouble(t["peak"]),
                sides = sides,
            )
            if (!StorageRules.valid(totals)) throw Bad()
            val decoded = StorageRules.decodeSides(sides)
            if (sides != null && (decoded.size != sides.split(',').size || decoded.any { !VersusRules.fits(it.side, mode) })) throw Bad()
            if (sides != null && !VersusRules.hasSides(mode)) throw Bad()
            return totals
        }

        /** Exactly the first N seat sides, each once; names cleaned (null = default label). */
        fun groupSeats(v: Any?): List<GroupSeat> {
            val items = list(v, GroupRules.MAX_SEATS, optional = false).map { obj(it) }
            if (items.size < GroupRules.MIN_SEATS) throw Bad()
            val seats = items.map { s -> GroupSeat(0, enum(s["side"]), s["name"]?.let { VersusRules.cleanName(str(it)) ?: throw Bad() }) }
            if (seats.map { it.side }.toSet() != GroupRules.sides(seats.size).toSet()) throw Bad()
            return GroupRules.ordered(seats)
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
