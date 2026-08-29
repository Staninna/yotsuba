package dev.stan.yotsuba.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.NavigationRow
import dev.stan.yotsuba.navigation.SettingsSectionId

/**
 * The settings index: one row per section, each showing enough of its current state that
 * the common questions ("is auto-refresh on?") are answered without opening anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenSection: (SettingsSectionId) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionId.entries.forEach { section ->
                NavigationRow(
                    icon = section.icon,
                    title = stringResource(section.titleRes),
                    summary = section.summary(state),
                    onClick = { onOpenSection(section) },
                )
            }
        }
    }
}

private val SettingsSectionId.icon: ImageVector
    get() = when (this) {
        SettingsSectionId.APPEARANCE -> Icons.Outlined.Palette
        SettingsSectionId.READING -> Icons.Outlined.MenuBook
        SettingsSectionId.MEDIA -> Icons.Outlined.PlayCircleOutline
        SettingsSectionId.BOARDS -> Icons.Outlined.GridView
        SettingsSectionId.LINKS -> Icons.Outlined.Shield
        SettingsSectionId.STORAGE -> Icons.Outlined.Storage
        SettingsSectionId.UPDATES -> Icons.Outlined.SystemUpdateAlt
        SettingsSectionId.ABOUT -> Icons.Outlined.Info
    }

internal val SettingsSectionId.titleRes: Int
    get() = when (this) {
        SettingsSectionId.APPEARANCE -> R.string.settings_appearance
        SettingsSectionId.READING -> R.string.settings_reading
        SettingsSectionId.MEDIA -> R.string.settings_media
        SettingsSectionId.BOARDS -> R.string.settings_boards
        SettingsSectionId.LINKS -> R.string.settings_links
        SettingsSectionId.STORAGE -> R.string.settings_storage
        SettingsSectionId.UPDATES -> R.string.settings_updates
        SettingsSectionId.ABOUT -> R.string.settings_about
    }

@Composable
private fun SettingsSectionId.summary(state: SettingsUiState): String {
    val s = state.settings
    val separator = " \u00b7 "
    return when (this) {
        SettingsSectionId.APPEARANCE ->
            stringResource(s.themeMode.labelRes) + separator + stringResource(s.catalogLayout.labelRes)
        SettingsSectionId.READING -> stringResource(
            R.string.settings_summary_reading,
            stringResource(if (s.autoRefreshEnabled) R.string.settings_on else R.string.settings_off),
            stringResource(s.historyRetention.labelRes),
        )
        SettingsSectionId.MEDIA -> stringResource(s.mediaAutoplay.labelRes)
        SettingsSectionId.BOARDS -> stringResource(R.string.settings_summary_boards, s.hiddenBoards.size)
        SettingsSectionId.LINKS -> stringResource(R.string.settings_summary_links, s.trustedDomains.size)
        SettingsSectionId.STORAGE -> stringResource(R.string.settings_summary_storage, state.hiddenThreads.size)
        SettingsSectionId.UPDATES -> stringResource(R.string.settings_version, state.versionName)
        SettingsSectionId.ABOUT -> stringResource(R.string.settings_summary_about)
    }
}
