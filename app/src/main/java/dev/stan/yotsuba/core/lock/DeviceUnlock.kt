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
     * Shows the system prompt. [onResult] is called once, on the main thread, with true only
     * for a passed unlock; a cancel, a lockout or a missing credential all count as false.
     * No negative button: the library forbids one when device credentials are allowed.
     */
    fun prompt(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return promptWithKeyguard(activity, title, onResult)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
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
    private fun promptWithKeyguard(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        val intent = keyguard?.createConfirmDeviceCredentialIntent(title, null) ?: return onResult(false)
        var launcher: ActivityResultLauncher<Intent>? = null
        launcher = activity.activityResultRegistry.register(
            KEYGUARD_RESULT_KEY, ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            launcher?.unregister()
            onResult(result.resultCode == Activity.RESULT_OK)
        }
        launcher.launch(intent)
    }
}
