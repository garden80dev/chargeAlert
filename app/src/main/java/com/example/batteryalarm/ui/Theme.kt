package com.example.batteryalarm.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** Palette vivace, pensata per lo sfondo nero dei display OLED. */
object Neon {
    val Red = Color(0xFFFF3B6B)
    val RedDeep = Color(0xFFE8264F)
    val Orange = Color(0xFFFF9F0A)
    val Yellow = Color(0xFFFFE135)
    val Green = Color(0xFF3CF28B)
    val Cyan = Color(0xFF00E0FF)
    val Magenta = Color(0xFFFF2DAA)
    val Violet = Color(0xFF8B5CFF)

    val Track = Color(0x1FFFFFFF)
    val TextDim = Color(0xFFB8C0CC)
    val TextMuted = Color(0xFF7A8494)
}

/**
 * Colori dell'anello in funzione della frazione di carica (0..1).
 * Rosso quando è scarica, verde alla soglia, magenta oltre la soglia (zona da evitare).
 */
fun ringStops(threshold: Float): List<Pair<Float, Color>> = listOf(
    0f to Neon.Red,
    threshold * 0.40f to Neon.Orange,
    threshold * 0.70f to Neon.Yellow,
    threshold to Neon.Green,
    1f to Neon.Magenta,
)

/** Colore dell'anello alla frazione [f], interpolato tra gli [stops]. */
fun colorAt(stops: List<Pair<Float, Color>>, f: Float): Color {
    val x = f.coerceIn(0f, 1f)
    for (i in 1 until stops.size) {
        val (p1, c1) = stops[i - 1]
        val (p2, c2) = stops[i]
        if (x <= p2) {
            val span = (p2 - p1).takeIf { it > 0f } ?: return c2
            return lerp(c1, c2, (x - p1) / span)
        }
    }
    return stops.last().second
}

private val NeonColorScheme = ColorScheme(
    primary = Neon.Green,
    primaryDim = Color(0xFF2BC46F),
    primaryContainer = Color(0xFF0D5C33),
    onPrimary = Color(0xFF00210F),
    onPrimaryContainer = Color(0xFFB8FFD5),
    secondary = Neon.Cyan,
    secondaryDim = Color(0xFF00B3CC),
    secondaryContainer = Color(0xFF004F5C),
    onSecondary = Color(0xFF001F24),
    onSecondaryContainer = Color(0xFFB3F5FF),
    tertiary = Neon.Magenta,
    tertiaryDim = Color(0xFFCC2488),
    tertiaryContainer = Color(0xFF6B0F4A),
    onTertiary = Color.White,
    onTertiaryContainer = Color(0xFFFFD6EE),
    surfaceContainerLow = Color(0xFF121418),
    surfaceContainer = Color(0xFF1B1E24),
    surfaceContainerHigh = Color(0xFF262A32),
    onSurface = Color.White,
    onSurfaceVariant = Neon.TextDim,
    outline = Neon.TextMuted,
    outlineVariant = Color(0xFF3A404C),
    background = Color.Black,
    onBackground = Color.White,
    error = Neon.Red,
    errorDim = Color(0xFFCC2F56),
    errorContainer = Color(0xFF6B1027),
    onError = Color.White,
    onErrorContainer = Color(0xFFFFD9E0),
)

@Composable
fun BatteryAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NeonColorScheme, content = content)
}
