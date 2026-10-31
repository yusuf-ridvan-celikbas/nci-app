package com.cansin.app.ui.theme

import com.cansin.app.prefs.Accent

data class AccentTones(val primary: Long, val onPrimary: Long, val container: Long, val onContainer: Long)

data class AccentPalette(val light: AccentTones, val dark: AccentTones)

data class NeutralTones(
    val surface: Long,
    val onSurface: Long,
    val surfaceVariant: Long,
    val onSurfaceVariant: Long,
    val outline: Long,
    val outlineVariant: Long,
    val containerLowest: Long,
    val containerLow: Long,
    val container: Long,
    val containerHigh: Long,
    val containerHighest: Long,
    val dim: Long,
    val bright: Long,
    val inverseSurface: Long,
    val inverseOnSurface: Long,
)

object Palettes {
    val accents: Map<Accent, AccentPalette> = mapOf(
        Accent.AMBER to AccentPalette(
            AccentTones(0xFF7A5900, 0xFFFFFFFF, 0xFFFFDEA3, 0xFF261900),
            AccentTones(0xFFF8BD48, 0xFF402D00, 0xFF5C4200, 0xFFFFDEA3),
        ),
        Accent.RED to AccentPalette(
            AccentTones(0xFFBA1A1A, 0xFFFFFFFF, 0xFFFFDAD6, 0xFF410002),
            AccentTones(0xFFFFB4AB, 0xFF690005, 0xFF93000A, 0xFFFFDAD6),
        ),
        Accent.BLUE to AccentPalette(
            AccentTones(0xFF0061A4, 0xFFFFFFFF, 0xFFD1E4FF, 0xFF001D36),
            AccentTones(0xFF9ECAFF, 0xFF003258, 0xFF00497D, 0xFFD1E4FF),
        ),
        Accent.YELLOW to AccentPalette(
            AccentTones(0xFF695F00, 0xFFFFFFFF, 0xFFF7E46C, 0xFF201C00),
            AccentTones(0xFFDAC851, 0xFF373100, 0xFF4F4800, 0xFFF7E46C),
        ),
        Accent.GREEN to AccentPalette(
            AccentTones(0xFF006E1C, 0xFFFFFFFF, 0xFF94F990, 0xFF002204),
            AccentTones(0xFF78DC77, 0xFF00390A, 0xFF005313, 0xFF94F990),
        ),
        Accent.PURPLE to AccentPalette(
            AccentTones(0xFF6750A4, 0xFFFFFFFF, 0xFFEADDFF, 0xFF21005D),
            AccentTones(0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF),
        ),
    )

    val neutralLight = NeutralTones(
        surface = 0xFFFBF9F8, onSurface = 0xFF1B1C1C,
        surfaceVariant = 0xFFE0E3E3, onSurfaceVariant = 0xFF444748,
        outline = 0xFF747878, outlineVariant = 0xFFC4C7C7,
        containerLowest = 0xFFFFFFFF, containerLow = 0xFFF5F3F3, container = 0xFFEFEDED,
        containerHigh = 0xFFE9E8E7, containerHighest = 0xFFE3E2E2,
        dim = 0xFFDBD9D9, bright = 0xFFFBF9F8,
        inverseSurface = 0xFF303030, inverseOnSurface = 0xFFF2F0F0,
    )

    val neutralDark = NeutralTones(
        surface = 0xFF131313, onSurface = 0xFFE5E2E1,
        surfaceVariant = 0xFF444748, onSurfaceVariant = 0xFFC4C7C7,
        outline = 0xFF8E9192, outlineVariant = 0xFF444748,
        containerLowest = 0xFF0E0E0E, containerLow = 0xFF1B1C1C, container = 0xFF1F2020,
        containerHigh = 0xFF2A2A2A, containerHighest = 0xFF353535,
        dim = 0xFF131313, bright = 0xFF393939,
        inverseSurface = 0xFFE5E2E1, inverseOnSurface = 0xFF303030,
    )

    val versusRed: AccentTones get() = accents.getValue(Accent.RED).light
    val versusBlue: AccentTones get() = accents.getValue(Accent.BLUE).light
    val danger: AccentPalette get() = accents.getValue(Accent.RED)
    val caution: AccentPalette get() = accents.getValue(Accent.AMBER)

    fun of(accent: Accent): AccentPalette = accents[accent] ?: accents.getValue(Accent.AMBER)
}
