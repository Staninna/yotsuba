package dev.stan.yotsuba.core.lock

import androidx.fragment.app.FragmentActivity

/**
 * Decides when the lock screen puts the system prompt up by itself: on its first showing,
 * and again each time the user comes back to the app while it is still locked. A prompt
 * the user just dismissed is not re-shown on the resume that dismissal causes, otherwise
 * cancelling the PIN screen on Android 9 and below would bounce straight back into it.
 * The "Unlock" button calls [prompt] directly.
 */
class LockPrompter(
    private val activity: FragmentActivity,
    private val title: () -> String,
    private val onUnlocked: () -> Unit,
) {
    private var inFlight = false
    private var promptOnResume = true

    fun prompt() {
        if (inFlight) return
        inFlight = true
        promptOnResume = false
        DeviceUnlock.prompt(activity, title()) { passed ->
            inFlight = false
            if (passed) {
                promptOnResume = true
                onUnlocked()
            }
        }
    }

    /** Call from the activity's onResume while locked. */
    fun onResume() {
        if (promptOnResume) prompt()
    }

    /** Call from the activity's onStop; a stop caused by our own prompt does not arm a re-prompt. */
    fun onStop() {
        if (!inFlight) promptOnResume = true
    }
}
