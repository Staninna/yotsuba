package dev.stan.yotsuba

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.stan.yotsuba.core.designsystem.theme.YotsubaTheme
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.navigation.AppNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        // Runs the one-time legacy media migration as soon as storage access exists.
        viewModel.onResumed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            YotsubaTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                AppNavHost()
            }
        }
    }
}
