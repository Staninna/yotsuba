package dev.stan.yotsuba.core.designsystem.theme

import androidx.compose.material3.ColorScheme
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
)

val LocalYotsubaColors = staticCompositionLocalOf {
    YotsubaColors(
        greentext = SeedColor,
        quotelink = Color(0xFF3D6B99),
        spoilerScrim = Color(0xFF222222),
        deadThread = Color(0xFF888888),
    )
}

fun yotsubaColors(scheme: ColorScheme, dark: Boolean): YotsubaColors = YotsubaColors(
    greentext = if (dark) Color(0xFFA6C761) else Color(0xFF5E7A16),
    quotelink = scheme.primary,
    spoilerScrim = scheme.surfaceVariant,
    deadThread = scheme.outline,
)
