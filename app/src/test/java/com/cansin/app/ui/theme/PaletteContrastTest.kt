package com.cansin.app.ui.theme

import com.cansin.app.prefs.Accent
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class PaletteContrastTest {
    private fun channel(argb: Long, shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Long) =
        0.2126 * channel(argb, 16) + 0.7152 * channel(argb, 8) + 0.0722 * channel(argb, 0)

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertAA(name: String, fg: Long, bg: Long) {
        val ratio = contrast(fg, bg)
        assertTrue("$name contrast %.2f < 4.5".format(ratio), ratio >= 4.5)
    }

    @Test
    fun everyAccentMeetsWcagAA() {
        for ((accent, palette) in Palettes.accents) {
            for ((mode, tones, n) in listOf(
                Triple("light", palette.light, Palettes.neutralLight),
                Triple("dark", palette.dark, Palettes.neutralDark),
            )) {
                val p = "$accent/$mode"
                assertAA("$p onPrimary", tones.onPrimary, tones.primary)
                assertAA("$p onContainer", tones.onContainer, tones.container)
                assertAA("$p primary on surface", tones.primary, n.surface)
                assertAA("$p primary on highest container", tones.primary, n.containerHighest)
            }
        }
    }

    @Test
    fun neutralsMeetWcagAA() {
        for (n in listOf(Palettes.neutralLight, Palettes.neutralDark)) {
            assertAA("onSurface", n.onSurface, n.surface)
            assertAA("onSurfaceVariant", n.onSurfaceVariant, n.surface)
            assertAA("onSurface on highest", n.onSurface, n.containerHighest)
            assertAA("onSurfaceVariant on highest", n.onSurfaceVariant, n.containerHighest)
            assertAA("inverse", n.inverseOnSurface, n.inverseSurface)
        }
    }

    @Test
    fun everyStaticAccentHasPalette() {
        Accent.entries.filter { it != Accent.DYNAMIC }.forEach { assertTrue(it.name, it in Palettes.accents) }
    }
}
