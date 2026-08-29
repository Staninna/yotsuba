package dev.stan.yotsuba.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stan.yotsuba.R
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.feature.settings.sections.AboutSection
import dev.stan.yotsuba.feature.settings.sections.AppearanceSection
import dev.stan.yotsuba.feature.settings.sections.BoardsSection
import dev.stan.yotsuba.feature.settings.sections.LinksSection
import dev.stan.yotsuba.feature.settings.sections.MediaSection
import dev.stan.yotsuba.feature.settings.sections.ReadingSection
import dev.stan.yotsuba.feature.settings.sections.StorageSection
import dev.stan.yotsuba.navigation.SettingsSectionId
import kotlinx.coroutines.launch

/**
 * One subscreen of the settings index. Owns the chrome that every section would otherwise
 * repeat — app bar, snackbar, the "are you sure?" dialog — and dispatches the body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSectionScreen(
    section: SettingsSectionId,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    val confirmThen: (Int, () -> Unit) -> Unit = { body, action -> confirmAction = ConfirmAction(body, action) }
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(section.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            val settings = state.settings
            val update: ((Settings) -> Settings) -> Unit = { viewModel.update(it) }
            when (section) {
                SettingsSectionId.APPEARANCE -> AppearanceSection(settings, update)
                SettingsSectionId.READING -> ReadingSection(settings, update)
                SettingsSectionId.MEDIA -> MediaSection(settings, update)
                SettingsSectionId.BOARDS -> BoardsSection(viewModel::onHideNsfwBoards, confirmThen)
                SettingsSectionId.LINKS -> LinksSection(settings, update)
                SettingsSectionId.STORAGE -> StorageSection(
                    settings = settings,
                    update = update,
                    hiddenThreads = state.hiddenThreads,
                    onClearCache = viewModel::onClearCache,
                    onClearHistory = viewModel::onClearHistory,
                    onClearBookmarks = viewModel::onClearBookmarks,
                    onUnhideThread = viewModel::onUnhideThread,
                    confirmThen = confirmThen,
                    showMessage = showMessage,
                )
                SettingsSectionId.UPDATES -> UpdatesSection(
                    state = updateState,
                    onCheck = viewModel::onCheckForUpdates,
                    onInstall = viewModel::onInstallUpdate,
                    canInstallPackages = viewModel::canInstallPackages,
                    onRequestInstallPermission = { context.startActivity(viewModel.unknownSourcesIntent()) },
                )
                SettingsSectionId.ABOUT -> AboutSection(state.versionName)
            }
        }
    }

    confirmAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(stringResource(R.string.settings_confirm_title)) },
            text = { Text(stringResource(pending.bodyRes)) },
            confirmButton = {
                TextButton(onClick = { pending.action(); confirmAction = null }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** A destructive action waiting behind the "are you sure?" dialog. */
private class ConfirmAction(val bodyRes: Int, val action: () -> Unit)
