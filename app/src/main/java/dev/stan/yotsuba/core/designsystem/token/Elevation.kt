package dev.stan.yotsuba.core.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Elevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level3: Dp = 3.dp,
)

val LocalElevation = staticCompositionLocalOf { Elevation() }
