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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.cansin.app.prefs.Accent

val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun CansinTheme(dark: Boolean, accent: Accent, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme(LocalContext.current, accent, dark), content = content)
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
