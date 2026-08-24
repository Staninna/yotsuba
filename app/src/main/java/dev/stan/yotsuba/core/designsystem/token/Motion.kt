package dev.stan.yotsuba.core.designsystem.token

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class Motion(
    val short: Int = 150,
    val medium: Int = 300,
    val long: Int = 450,
)

val LocalMotion = staticCompositionLocalOf { Motion() }
