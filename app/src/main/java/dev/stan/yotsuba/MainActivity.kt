package dev.stan.yotsuba

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.stan.yotsuba.core.designsystem.theme.YotsubaTheme
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.navigation.AppNavHost
import dev.stan.yotsuba.navigation.ShellViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val shell: ShellViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        // Runs the one-time legacy media migration as soon as storage access exists.
        viewModel.onResumed()
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
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            YotsubaTheme(darkTheme = dark, dynamicColor = settings.dynamicColor, reduceMotion = settings.reduceMotion) {
                NotificationPermissionPrompt()
                AppNavHost(shell = shell)
            }
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
