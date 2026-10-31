package com.cansin.app.domain

import com.cansin.app.data.BeerEvent
import com.cansin.app.data.FriendProfile
import com.cansin.app.data.Note
import com.cansin.app.data.Session
import com.cansin.app.data.Side
import com.cansin.app.data.WaterEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportJsonTest {
    private val profile = BodyProfile(Sex.FEMALE, 30, 170, 60, License.NORMAL)
    private val data = ExportData(
        sessions = listOf(Session(id = 1, startedAt = 10, endedAt = 20)),
        beers = listOf(BeerEvent(sessionId = 1, at = 11)),
        water = listOf(WaterEvent(sessionId = 1, at = 12)),
        notes = listOf(Note(sessionId = 1, text = "say \"hi\"\n\\ok\u0001", createdAt = 13, side = Side.RED)),
        friends = listOf(FriendProfile(id = 4, name = "Ali", color = 0)),
        profile = profile,
    )

    @Test
    fun currencyOnlyWhenSet() {
        assertFalse(ExportJson.build(data, includeProfile = false, exportedAt = 0).contains("currency"))
        assertTrue(ExportJson.build(data.copy(currency = Currency.TRY), includeProfile = false, exportedAt = 0).contains("\"currency\":\"TRY\""))
    }

    @Test
    fun profileOnlyWhenTicked() {
        assertFalse(ExportJson.build(data, includeProfile = false, exportedAt = 0).contains("bodyProfile"))
        assertTrue(ExportJson.build(data, includeProfile = true, exportedAt = 0).contains("\"bodyProfile\":{\"sex\":\"FEMALE\",\"age\":30"))
    }

    @Test
    fun escapesStrings() {
        val json = ExportJson.build(data, false, 0)
        assertTrue(json.contains("\"text\":\"say \\\"hi\\\"\\n\\\\ok\\u0001\""))
    }

    @Test
    fun nestsEventsUnderSessions() {
        val json = ExportJson.build(data, false, 99)
        assertTrue(json.startsWith("{\"format\":\"cansin-export\",\"version\":1,\"exportedAt\":99,"))
        assertTrue(json.contains("\"beers\":[{\"side\":\"SOLO\",\"sizeMl\":500,\"abv\":5.0,\"at\":11,\"finishedAt\":null}]"))
        assertTrue(json.contains("\"water\":[{\"at\":12}]"))
        assertTrue(json.contains("\"friends\":[{\"id\":4,\"name\":\"Ali\"}]"))
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
    }

    @Test
    fun emptyExport() {
        val json = ExportJson.build(ExportData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null), true, 0)
        assertEquals("{\"format\":\"cansin-export\",\"version\":1,\"exportedAt\":0,\"sessions\":[],\"friends\":[],\"friendResults\":[]}", json)
    }
}
