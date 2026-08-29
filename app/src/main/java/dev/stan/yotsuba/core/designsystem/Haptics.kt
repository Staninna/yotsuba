package dev.stan.yotsuba.core.designsystem

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * The app's four haptic gestures. Every call site goes through this so the mapping from
 * "what happened" to "what the phone does" lives in one place.
 *
 * The platform honours the system "touch feedback" setting for every constant used here,
 * so there is nothing to check on our side.
 */
@Stable
class Haptics internal constructor(
    private val compose: HapticFeedback,
    private val view: View?,
) {
    /** A long press was recognised: a sheet or menu is about to open. */
    fun longPress() = compose.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Something succeeded: a save landed, a delete committed, a mode was entered. */
    fun confirm() = platform(HapticFeedbackConstants.CONFIRM, HapticFeedbackType.LongPress)

    /** Something failed: a save errored. */
    fun reject() = platform(HapticFeedbackConstants.REJECT, HapticFeedbackType.LongPress)

    /** A light tick: a jump landed, a spoiler opened, a toggle flipped. */
    fun tick() = compose.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** CONFIRM/REJECT exist from API 30; older devices get the closest Compose type. */
    private fun platform(constant: Int, fallback: HapticFeedbackType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && view != null) {
            view.performHapticFeedback(constant)
        } else {
            compose.performHapticFeedback(fallback)
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val compose = LocalHapticFeedback.current
    val view = LocalView.current
    return remember(compose, view) { Haptics(compose, view) }
}
