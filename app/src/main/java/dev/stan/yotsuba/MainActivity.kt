package dev.stan.yotsuba

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.stan.yotsuba.core.designsystem.theme.YotsubaTheme
import dev.stan.yotsuba.core.lock.AppLock
import dev.stan.yotsuba.core.lock.LockPrompter
import dev.stan.yotsuba.feature.lock.LockScreen
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.navigation.AppNavHost
import dev.stan.yotsuba.navigation.ShellViewModel
import javax.inject.Inject

@AndroidEntryPoint
// FragmentActivity rather than ComponentActivity only because BiometricPrompt wants one.
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val shell: ShellViewModel by viewModels()

    @Inject lateinit var appLock: AppLock

    private val lockPrompter by lazy {
        LockPrompter(this, title = { getString(R.string.lock_prompt_title) }, onUnlocked = appLock::unlock)
    }

    override fun onResume() {
        super.onResume()
        // Runs the one-time legacy media migration as soon as storage access exists.
        viewModel.onResumed()
        if (appLock.locked.value) lockPrompter.onResume()
    }

    override fun onStop() {
        super.onStop()
        lockPrompter.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shell.onIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Browser link, shared URL or widget tap: parked until the nav graph is up.
        if (savedInstanceState == null) shell.onIntent(intent)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            LaunchedEffect(settings.appLock) { hideFromRecents(settings.appLock) }
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val lockReady by appLock.ready.collectAsStateWithLifecycle()
            val locked by appLock.locked.collectAsStateWithLifecycle()
            YotsubaTheme(darkTheme = dark, dynamicColor = settings.dynamicColor, reduceMotion = settings.reduceMotion) {
                when {
                    // Settings not read yet: a blank surface, never a glimpse of the content.
                    !lockReady -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    locked -> {
                        LaunchedEffect(Unit) { lockPrompter.prompt() }
                        LockScreen(onUnlock = lockPrompter::prompt)
                    }
                    else -> {
                        NotificationPermissionPrompt()
                        AppNavHost(shell = shell)
                    }
                }
            }
        }
    }

    /**
     * With the lock on, the recents carousel must not show the last screen. Android 13 can
     * blank just the recents card; older releases need FLAG_SECURE, which also blocks
     * screenshots. The flag follows the setting, not the lock state, because the snapshot is
     * taken when the app goes to the background, before the lock decision is made.
     */
    private fun hideFromRecents(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!hide)
        } else if (hide) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * Asks for POST_NOTIFICATIONS once, and only once there is a reason: the user keeps
     * bookmark notifications on and has something bookmarked. Android 12 and below grant
     * it implicitly.
     */
    @Composable
    private fun NotificationPermissionPrompt() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val wants by shell.wantsNotifications.collectAsStateWithLifecycle()
        var asked by rememberSaveable { mutableStateOf(false) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(wants) {
            if (!wants || asked) return@LaunchedEffect
            val granted = ContextCompat.checkSelfPermission(
                this@MainActivity, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            asked = true
            if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
