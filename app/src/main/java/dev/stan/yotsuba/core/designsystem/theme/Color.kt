package dev.stan.yotsuba.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Seed: 4chan greentext green (D18). */
val SeedColor = Color(0xFF789922)

/** Semantic extras derived from the active scheme (§4). */
@Immutable
data class YotsubaColors(
    val greentext: Color,
    val quotelink: Color,
    val spoilerScrim: Color,
    val deadThread: Color,
    /** A save that landed; drawn over the viewer's black chrome and thumbnail scrims. */
    val saveSuccess: Color,
    /** A save that failed; same ground as [saveSuccess]. */
    val saveError: Color,
)

/** Outside [dev.stan.yotsuba.core.designsystem.theme.YotsubaTheme] (a bare preview) the light values apply; the function is the one definition. */
val LocalYotsubaColors = staticCompositionLocalOf { yotsubaColors(lightColorScheme(), dark = false) }

fun yotsubaColors(scheme: ColorScheme, dark: Boolean): YotsubaColors = YotsubaColors(
    greentext = if (dark) Color(0xFFA6C761) else Color(0xFF5E7A16),
    quotelink = scheme.primary,
    spoilerScrim = scheme.surfaceVariant,
    deadThread = scheme.outline,
    // Both always sit on black, whatever the theme, so a light scheme's roles would lose
    // their contrast there. They take the dark scheme's tones in either theme: the dark
    // greentext green, and the dark scheme's error red.
    saveSuccess = Color(0xFFA6C761),
    saveError = if (dark) scheme.error else DarkErrorTone,
)

/** M3's error role at tone 80, what a dark scheme's `error` resolves to. */
private val DarkErrorTone = Color(0xFFF2B8B5)
