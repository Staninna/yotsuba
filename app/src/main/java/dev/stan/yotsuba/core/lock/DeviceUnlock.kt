package dev.stan.yotsuba.core.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * The phone's own unlock, as the app uses it: any biometric the phone trusts, or its PIN,
 * pattern or password. A phone with no screen lock at all fails [available], and the
 * settings row refuses to turn the lock on rather than lock the user out.
 *
 * Android 8.x gets the system's confirm-credential screen instead of BiometricPrompt: below
 * API 28 the library draws its own fingerprint dialog with AppCompat widgets, which the
 * app's platform theme cannot inflate.
 */
/** How a prompt ended. [CANCELLED] is the user backing out; [FAILED] is everything else. */
enum class UnlockResult { PASSED, CANCELLED, FAILED }

object DeviceUnlock {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    private const val KEYGUARD_RESULT_KEY = "dev.stan.yotsuba.core.lock.keyguard"

    fun available(context: Context): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        } else {
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
        }

    /**
     * Shows the system prompt. [onResult] is called once, on the main thread. A cancel by the
     * user is reported apart from a timeout, a lockout or the system dismissing the prompt,
     * because only the user's own cancel should stop the app asking again on the next resume.
     * No negative button: the library forbids one when device credentials are allowed.
     */
    fun prompt(activity: FragmentActivity, title: String, onResult: (UnlockResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return promptWithKeyguard(activity, title, onResult)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(UnlockResult.PASSED)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON -> UnlockResult.CANCELLED
                    else -> UnlockResult.FAILED
                },
            )
            // A single failed attempt keeps the prompt open; only the error ends it.
            override fun onAuthenticationFailed() {}
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }

    @Suppress("DEPRECATION") // The replacement is BiometricPrompt, which is what API 28+ uses.
    private fun promptWithKeyguard(activity: FragmentActivity, title: String, onResult: (UnlockResult) -> Unit) {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        val intent = keyguard?.createConfirmDeviceCredentialIntent(title, null) ?: return onResult(UnlockResult.FAILED)
        var launcher: ActivityResultLauncher<Intent>? = null
        launcher = activity.activityResultRegistry.register(
            KEYGUARD_RESULT_KEY, ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            launcher?.unregister()
            onResult(if (result.resultCode == Activity.RESULT_OK) UnlockResult.PASSED else UnlockResult.CANCELLED)
        }
        launcher.launch(intent)
    }
}
