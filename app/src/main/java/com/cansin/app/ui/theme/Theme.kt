package com.cansin.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.cansin.app.prefs.Accent

val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Immutable
data class AlertColors(val danger: Color, val onDanger: Color, val caution: Color, val onCaution: Color)

val LocalAlertColors = staticCompositionLocalOf { alertColors(dark = false) }

fun alertColors(dark: Boolean): AlertColors {
    val danger = if (dark) Palettes.danger.dark else Palettes.danger.light
    val caution = if (dark) Palettes.caution.dark else Palettes.caution.light
    return AlertColors(Color(danger.primary), Color(danger.onPrimary), Color(caution.container), Color(caution.onContainer))
}

@Immutable
data class SideColors(val red: Color, val onRed: Color, val blue: Color, val onBlue: Color)

val VersusColors = SideColors(
    Color(Palettes.versusRed.primary),
    Color(Palettes.versusRed.onPrimary),
    Color(Palettes.versusBlue.primary),
    Color(Palettes.versusBlue.onPrimary),
)

/** Fixed seat colours for group mode (light tones, same in every theme like Versus RED/BLUE); AA-tested via Palettes. */
val SeatAccents: Map<com.cansin.app.data.Side, Accent> = mapOf(
    com.cansin.app.data.Side.RED to Accent.RED,
    com.cansin.app.data.Side.BLUE to Accent.BLUE,
    com.cansin.app.data.Side.GREEN to Accent.GREEN,
    com.cansin.app.data.Side.PURPLE to Accent.PURPLE,
    com.cansin.app.data.Side.AMBER to Accent.AMBER,
    com.cansin.app.data.Side.YELLOW to Accent.YELLOW,
)

fun seatColors(side: com.cansin.app.data.Side?): Pair<Color, Color>? =
    SeatAccents[side]?.let { Palettes.accents.getValue(it).light }?.let { Color(it.primary) to Color(it.onPrimary) }

@Composable
fun CansinTheme(dark: Boolean, accent: Accent, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAlertColors provides alertColors(dark)) {
        MaterialTheme(colorScheme = colorScheme(LocalContext.current, accent, dark), content = content)
    }
}

fun colorScheme(context: Context, accent: Accent, dark: Boolean): ColorScheme =
    if (accent == Accent.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        schemeFor(accent, dark)
    }

private fun schemeFor(accent: Accent, dark: Boolean): ColorScheme {
    val palette = Palettes.of(accent)
    val a = if (dark) palette.dark else palette.light
    val inverse = if (dark) palette.light else palette.dark
    val n = if (dark) Palettes.neutralDark else Palettes.neutralLight
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(a.primary),
        onPrimary = Color(a.onPrimary),
        primaryContainer = Color(a.container),
        onPrimaryContainer = Color(a.onContainer),
        inversePrimary = Color(inverse.primary),
        secondary = Color(a.primary),
        onSecondary = Color(a.onPrimary),
        secondaryContainer = Color(a.container),
        onSecondaryContainer = Color(a.onContainer),
        tertiary = Color(a.primary),
        onTertiary = Color(a.onPrimary),
        tertiaryContainer = Color(a.container),
        onTertiaryContainer = Color(a.onContainer),
        surfaceTint = Color(a.primary),
        background = Color(n.surface),
        onBackground = Color(n.onSurface),
        surface = Color(n.surface),
        onSurface = Color(n.onSurface),
        surfaceVariant = Color(n.surfaceVariant),
        onSurfaceVariant = Color(n.onSurfaceVariant),
        outline = Color(n.outline),
        outlineVariant = Color(n.outlineVariant),
        surfaceContainerLowest = Color(n.containerLowest),
        surfaceContainerLow = Color(n.containerLow),
        surfaceContainer = Color(n.container),
        surfaceContainerHigh = Color(n.containerHigh),
        surfaceContainerHighest = Color(n.containerHighest),
        surfaceDim = Color(n.dim),
        surfaceBright = Color(n.bright),
        inverseSurface = Color(n.inverseSurface),
        inverseOnSurface = Color(n.inverseOnSurface),
    )
}
