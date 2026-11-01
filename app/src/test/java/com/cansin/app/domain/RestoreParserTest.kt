package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.FriendProfile
import com.cansin.app.data.FriendResult
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.SessionMode
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
            BeerEvent(sessionId = 2, side = Side.BLUE, at = 31),
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
        assertEquals(RestoreError.INVALID, fail(json.replace("\"age\":30", "\"age\":12")))
        assertEquals(RestoreError.INVALID, fail(json.replace("\"endedAt\":20", "\"endedAt\":null")))
        assertEquals(RestoreError.INVALID, fail(json.replace("\"text\":\"nuts\"", "\"text\":\"   \"")))
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
}
