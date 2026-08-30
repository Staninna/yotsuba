package dev.stan.yotsuba.core.lock

import androidx.fragment.app.FragmentActivity

/**
 * Puts the system prompt up by itself whenever the locked app comes to the front: on the
 * first showing and on every resume after that, so a prompt the system dismissed (face
 * timed out, app sent to the background with the prompt open) comes back on its own.
 *
 * The one exception is the user's own cancel. On the keyguard path (Android 8.x) and the
 * older device-credential fallback, cancelling finishes a system activity and resumes ours,
 * so re-prompting there would bounce the user straight back into the PIN screen; that resume
 * is skipped once. When the cancel happens over a still-resumed activity there is no such
 * resume, and the next stop clears the skip so the next return prompts again.
 *
 * [show] is the prompt itself; production passes [DeviceUnlock.prompt], tests a fake.
 */
class LockPrompter(
    private val title: () -> String,
    private val onUnlocked: () -> Unit,
    private val show: (String, (UnlockResult) -> Unit) -> Unit,
) {
    constructor(activity: FragmentActivity, title: () -> String, onUnlocked: () -> Unit) :
        this(title, onUnlocked, { t, cb -> DeviceUnlock.prompt(activity, t, cb) })

    private var inFlight = false
    private var skipNextResume = false

    fun prompt() {
        if (inFlight) return
        inFlight = true
        show(title()) { result ->
            inFlight = false
            when (result) {
                UnlockResult.PASSED -> onUnlocked()
                UnlockResult.CANCELLED -> skipNextResume = true
                UnlockResult.FAILED -> Unit
            }
        }
    }

    /** Call from the activity's onResume while locked. */
    fun onResume() {
        if (skipNextResume) {
            skipNextResume = false
            return
        }
        prompt()
    }

    /** Call from the activity's onStop. A stop with no prompt open means the user left on their own. */
    fun onStop() {
        if (!inFlight) skipNextResume = false
    }
}
