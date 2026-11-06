package com.cansin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrandFilterTest {
    @Test
    fun brandsAreRemovedFromText() {
        assertEquals("Bira", BrandFilter.strip("Belfast Bira"))
        assertEquals("bira ×2", BrandFilter.strip("efes   bira ×2"))
        assertEquals("Rakı", BrandFilter.strip("Yeni Rakı"))
        assertEquals("Tekirdağ'a gittik", BrandFilter.strip("Tekirdağ'a gittik")) // place name stays
        assertEquals("Shot", BrandFilter.strip("Jägermeister Shot"))
        assertEquals("", BrandFilter.strip("Johnnie  Walker"))
        assertEquals("Efe ile çerez", BrandFilter.strip("Efe ile çerez")) // first name stays
        assertEquals("Belfastlı arkadaş", BrandFilter.strip("Belfastlı arkadaş")) // only whole words
    }

    @Test
    fun notesAndQuestionsUseTheFilter() {
        assertEquals("Bira", NoteRules.cleanText(" Belfast Bira "))
        assertNull(NoteRules.cleanText("Tuborg"))
        assertEquals("En sevdiğin bira hangisi?", GameRules.cleanQuestion("En sevdiğin Efes bira hangisi?"))
    }
}
