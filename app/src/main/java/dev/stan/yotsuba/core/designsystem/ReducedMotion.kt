package dev.stan.yotsuba.core.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The in-app "Reduce motion" setting, provided once at the root by the theme. Default
 * false so previews and tests without a theme still animate.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * True when the user has turned animations off in developer or accessibility settings
 * (animator duration scale 0). Every decorative animation collapses to its end state.
 */
fun isReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** [isReducedMotion] for callers that also hold the in-app setting: either source wins. */
fun isReducedMotion(context: Context, reduceMotionSetting: Boolean): Boolean =
    reduceMotionSetting || isReducedMotion(context)

/** The in-app switch or the system animator scale, whichever asks for less motion. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val system = remember(context) { isReducedMotion(context) }
    return LocalReduceMotion.current || system
}
