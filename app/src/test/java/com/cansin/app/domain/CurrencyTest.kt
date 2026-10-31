package com.cansin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyTest {
    @Test
    fun formatsWithSymbolPlacement() {
        assertEquals("12,50", Currency.NONE.format(1250, ',', turkish = true))
        assertEquals("12,50 ₺", Currency.TRY.format(1250, ',', turkish = true))
        assertEquals("12.50 ₺", Currency.TRY.format(1250, '.', turkish = false))
        assertEquals("12,50 €", Currency.EUR.format(1250, ',', turkish = true))
        assertEquals("€12.50", Currency.EUR.format(1250, '.', turkish = false))
        assertEquals("$0.05", Currency.USD.format(5, '.', turkish = true))
        assertEquals("£1000000.00", Currency.GBP.format(100_000_000, '.', turkish = false))
    }
}
