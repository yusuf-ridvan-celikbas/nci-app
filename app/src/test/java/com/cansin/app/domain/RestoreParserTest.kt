package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.DrinkKind
import com.cansin.app.data.FriendProfile
import com.cansin.app.data.FriendResult
import com.cansin.app.data.GroupSeat
import com.cansin.app.data.CustomQuestion
import com.cansin.app.data.QuestionKind
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
import com.cansin.app.data.SessionTotals
import com.cansin.app.data.Side
import com.cansin.app.data.WaterEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreParserTest {
    private val now = 1_000_000_000L
    private val profile = BodyProfile(Sex.FEMALE, 30, 170, 60, License.NORMAL)
    private val data = ExportData(
        sessions = listOf(
            Session(id = 1, startedAt = 10, endedAt = 20),
            Session(id = 2, startedAt = 30, endedAt = null, mode = SessionMode.VERSUS, redName = "Me", blueName = "Ali", blueFriendId = 4, meSide = Side.RED),
        ),
        beers = listOf(
            BeerEvent(sessionId = 1, at = 11, sizeMl = 330, abv = 4.5, finishedAt = 15),
            BeerEvent(sessionId = 2, side = Side.BLUE, at = 31, sizeMl = 40, abv = 40.0, kind = DrinkKind.SHOT),
        ),
        water = listOf(WaterEvent(sessionId = 1, at = 12)),
        notes = listOf(
            Note(sessionId = 1, text = "say \"hi\"\n\\ok ğüş", person = "Ali", amountMinor = 1250, createdAt = 13),
            Note(sessionId = 2, text = "nuts", createdAt = 32, side = Side.BLUE),
        ),
        friends = listOf(FriendProfile(id = 4, name = "Ali", color = 0)),
        profile = profile,
        friendResults = listOf(FriendResult(name = "Can", day = "2026-10-31", beers = 3, durationMin = 90, water = 2, importedAt = 5)),
        currency = Currency.TRY,
    )

    private fun ok(json: String) = (RestoreParser.parse(json, now) as RestoreResult.Ok).data
    private fun fail(json: String) = (RestoreParser.parse(json, now) as RestoreResult.Fail).error
    private val json = ExportJson.build(data, includeProfile = true, exportedAt = 99)

    @Test
    fun roundTripsOurExport() {
        val r = ok(json)
        assertEquals(2, r.sessions.size)
        val solo = r.sessions[0]
        assertEquals(Session(startedAt = 10, endedAt = 20), solo.session)
        assertEquals(listOf(BeerEvent(sessionId = 0, at = 11, sizeMl = 330, abv = 4.5, finishedAt = 15)), solo.beers)
        assertEquals(listOf(WaterEvent(sessionId = 0, at = 12)), solo.water)
        assertEquals("say \"hi\"\n\\ok ğüş", solo.notes.single().text)
        assertEquals(1250L, solo.notes.single().amountMinor)
        val versus = r.sessions[1].session
        assertEquals(4L, versus.blueFriendId)
        assertEquals(Side.RED, versus.meSide)
        assertNull(versus.endedAt)
        assertEquals(Side.BLUE, r.sessions[1].notes.single().side)
        assertEquals(listOf(RestoreFriend(4, "Ali")), r.friends)
        assertEquals("Can", r.friendResults.single().name)
        assertEquals(now, r.friendResults.single().importedAt)
        assertEquals(Currency.TRY, r.currency)
        assertEquals(profile, r.profile)
        assertEquals(2, r.beerCount)
        assertEquals(DrinkKind.SHOT, r.sessions[1].beers.single().kind)
        assertEquals(DrinkKind.BEER, solo.beers.single().kind)
    }

    @Test
    fun profileAndCurrencyOptional() {
        val r = ok(ExportJson.build(data.copy(currency = Currency.NONE), includeProfile = false, exportedAt = 0))
        assertNull(r.profile)
        assertNull(r.currency)
    }

    @Test
    fun rejectsOtherFilesAndVersions() {
        assertEquals(RestoreError.NOT_OURS, fail("""{"format":"other","version":1,"sessions":[]}"""))
        assertEquals(RestoreError.NOT_OURS, fail("""[1,2]"""))
        assertEquals(RestoreError.NEWER_VERSION, fail("""{"format":"cansin-export","version":2,"sessions":[]}"""))
        assertEquals(RestoreError.INVALID, fail("""{"format":"cansin-export","version":1}"""))
        assertEquals(RestoreError.INVALID, fail("not json"))
        assertEquals(RestoreError.INVALID, fail(json.dropLast(1)))
        assertEquals(RestoreError.INVALID, fail("""{"format":"cansin-export","format":"cansin-export","version":1,"sessions":[]}"""))
        assertTrue(ok("""{"format":"cansin-export","version":1,"sessions":[]}""").sessions.isEmpty())
    }

    @Test
    fun rejectsBadItems() {
        assertEquals(RestoreError.INVALID, fail(json.replace("\"sizeMl\":330", "\"sizeMl\":5000")))
        assertEquals(RestoreError.INVALID, fail(json.replace("\"abv\":4.5", "\"abv\":-1")))
        assertEquals(RestoreError.INVALID, fail(json.replace("\"endedAt\":20", "\"endedAt\":5")))
        assertEquals(RestoreError.INVALID, fail(json.replace("\"startedAt\":10", "\"startedAt\":${now * 2}")))
        assertEquals(RestoreError.INVALID, fail(json.replace("\"side\":\"SOLO\"", "\"side\":\"RED\"")))
        assertEquals(RestoreError.INVALID, fail(json.replace("\"blueFriendId\":4", "\"blueFriendId\":9")))
        assertEquals(RestoreError.INVALID, fail(json.replace("\"amountMinor\":1250", "\"amountMinor\":-1")))
        assertEquals(RestoreError.INVALID, fail(json.replace("\"mode\":\"SOLO\"", "\"mode\":\"PARTY\"")))
        assertEquals(RestoreError.INVALID, fail(json.replace("\"kind\":\"SHOT\"", "\"kind\":\"WATER\"")))
        assertEquals(RestoreError.INVALID, fail(json.replace("\"age\":30", "\"age\":12")))
        assertEquals(RestoreError.INVALID, fail(json.replace("\"endedAt\":20", "\"endedAt\":null")))
        assertEquals(RestoreError.INVALID, fail(json.replace("\"text\":\"nuts\"", "\"text\":\"   \"")))
    }

    private val groupData = ExportData(
        sessions = listOf(Session(id = 5, startedAt = 10, endedAt = 50, mode = SessionMode.GROUP, meSide = Side.GREEN)),
        beers = listOf(BeerEvent(sessionId = 5, side = Side.GREEN, at = 11), BeerEvent(sessionId = 5, side = Side.RED, at = 12)),
        water = listOf(WaterEvent(sessionId = 5, at = 13, side = Side.BLUE)),
        notes = listOf(Note(sessionId = 5, text = "chips", amountMinor = 300, createdAt = 14, side = Side.RED)),
        friends = emptyList(),
        profile = null,
        seats = listOf(GroupSeat(5, Side.GREEN, "Can"), GroupSeat(5, Side.RED, "Ali"), GroupSeat(5, Side.BLUE, null)),
    )
    private val groupJson = ExportJson.build(groupData, includeProfile = false, exportedAt = 0)

    @Test
    fun groupRoundTrips() {
        val r = ok(groupJson).sessions.single()
        assertEquals(SessionMode.GROUP, r.session.mode)
        assertEquals(Side.GREEN, r.session.meSide)
        assertEquals(listOf(GroupSeat(0, Side.RED, "Ali"), GroupSeat(0, Side.BLUE, null), GroupSeat(0, Side.GREEN, "Can")), r.seats)
        assertEquals(Side.BLUE, r.water.single().side)
        assertEquals(Side.RED, r.notes.single().side)
        assertNull(ok(json).sessions[0].water.single().side)
    }

    @Test
    fun rejectsBadGroups() {
        assertEquals(RestoreError.INVALID, fail(groupJson.replace("\"side\":\"GREEN\",\"name\":\"Can\"", "\"side\":\"YELLOW\",\"name\":\"Can\"")))
        assertEquals(RestoreError.INVALID, fail(groupJson.replace("{\"at\":13,\"side\":\"BLUE\"}", "{\"at\":13}")))
        assertEquals(RestoreError.INVALID, fail(groupJson.replace("\"meSide\":\"GREEN\"", "\"meSide\":\"PURPLE\"")))
        assertEquals(RestoreError.INVALID, fail(groupJson.replace("\"mode\":\"GROUP\"", "\"mode\":\"VERSUS\"")))
        assertEquals(RestoreError.INVALID, fail(json.replace("{\"at\":12}", "{\"at\":12,\"side\":\"RED\"}")))
    }

    @Test
    fun customQuestionsRoundTrip() {
        val qs = listOf(CustomQuestion(9, QuestionKind.DARE, "Sing", 20), CustomQuestion(10, QuestionKind.TRUTH, "Why?", 30))
        val j = ExportJson.build(data.copy(customQuestions = qs), includeProfile = false, exportedAt = 0)
        assertEquals(qs.map { it.copy(id = 0) }, ok(j).customQuestions)
        assertTrue(ok(json).customQuestions.isEmpty())
        assertEquals(RestoreError.INVALID, fail(j.replace("\"kind\":\"DARE\"", "\"kind\":\"DRINK\"")))
        assertEquals(RestoreError.INVALID, fail(j.replace("\"text\":\"Sing\"", "\"text\":\"   \"")))
    }

    @Test
    fun capsSize() {
        assertEquals(RestoreError.TOO_LARGE, fail(" ".repeat(RestoreParser.MAX_BYTES + 1)))
        val deep = "[".repeat(50) + "]".repeat(50)
        assertEquals(RestoreError.INVALID, fail(deep))
    }

    @Test
    fun miniJsonBasics() {
        assertEquals(mapOf("a" to listOf(1L, -2.5, true, null, "xé")), MiniJson.parse("""{ "a": [1, -2.5, true, null, "xé"] }"""))
        assertEquals("\t/", MiniJson.parse("\"\\t\\/\""))
    }

    private val compressed = SessionTotals(15, 3, 2, 1, 1, 1, 300, 12.5, 0.42, "RED:2:1,BLUE:1:0")
    private val compressedData = ExportData(
        sessions = listOf(Session(id = 7, startedAt = 10, endedAt = 50, mode = SessionMode.VERSUS, meSide = Side.RED, totals = compressed)),
        beers = emptyList(), water = emptyList(), notes = emptyList(), friends = emptyList(), profile = null,
    )
    private val compressedJson = ExportJson.build(compressedData, includeProfile = false, exportedAt = 0)

    @Test
    fun compressedTotalsRoundTrip() {
        assertEquals(compressed, ok(compressedJson).sessions.single().session.totals)
        assertNull(ok(json).sessions[0].session.totals)
    }

    @Test
    fun rejectsBadTotals() {
        assertEquals(RestoreError.INVALID, fail(compressedJson.replace("\"drinks\":3", "\"drinks\":-3")))
        assertEquals(RestoreError.INVALID, fail(compressedJson.replace("\"sides\":\"RED:2:1,BLUE:1:0\"", "\"sides\":\"RED:2:1,TEAL:1:0\"")))
        assertEquals(RestoreError.INVALID, fail(compressedJson.replace("\"endedAt\":50", "\"endedAt\":null")))
        assertEquals(RestoreError.INVALID, fail(compressedJson.replace("\"beers\":[]", "\"beers\":[{\"side\":\"RED\",\"sizeMl\":500,\"abv\":5.0,\"at\":11}]")))
    }

    @Test
    fun drinkPricesRoundTrip() {
        val priced = data.copy(
            beers = data.beers.map { if (it.sessionId == 1L) it.copy(priceMinor = 15_000, brand = "Efes") else it },
            water = listOf(WaterEvent(sessionId = 1, at = 12, priceMinor = 2_000, sizeMl = 1_000)),
            prices = mapOf(PriceSlot.BEER to 15_000L, PriceSlot.BEER_1L to 28_000L, PriceSlot.RAKI to 22_000L),
        )
        val j = ExportJson.build(priced, includeProfile = false, exportedAt = 0)
        val r = ok(j)
        assertEquals(15_000L, r.sessions[0].beers.single().priceMinor)
        assertEquals("Efes", r.sessions[0].beers.single().brand)
        assertEquals(2_000L, r.sessions[0].water.single().priceMinor)
        assertEquals(1_000, r.sessions[0].water.single().sizeMl)
        assertEquals(RestoreError.INVALID, fail(j.replace("\"sizeMl\":1000", "\"sizeMl\":333")))
        assertEquals(RestoreError.INVALID, fail(j.replace("\"brand\":\"Efes\"", "\"brand\":\"   \"")))
        assertNull(r.sessions[1].beers.single().priceMinor)
        assertEquals(mapOf(PriceSlot.BEER to 15_000L, PriceSlot.BEER_1L to 28_000L, PriceSlot.RAKI to 22_000L), r.prices)
        assertNull(ok(json).prices)
        assertEquals(RestoreError.INVALID, fail(j.replace("\"priceMinor\":15000", "\"priceMinor\":-1")))
        assertEquals(RestoreError.INVALID, fail(j.replace("\"RAKI\":22000", "\"CUSTOM\":22000")))
    }
}
