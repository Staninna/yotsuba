package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.TextRow
import dev.stan.yotsuba.feature.settings.ManagedListDialog
import dev.stan.yotsuba.feature.settings.SettingsUiState
import dev.stan.yotsuba.feature.settings.SettingsViewModel

@Composable
fun StorageSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    confirmThen: (Int, () -> Unit) -> Unit,
    showMessage: (String) -> Unit,
) {
    var showHidden by remember { mutableStateOf(false) }
    val cleared = stringResource(R.string.settings_cleared)

    fun clear(bodyRes: Int, action: () -> Unit) = confirmThen(bodyRes) {
        action()
        showMessage(cleared)
    }

    TextRow(stringResource(R.string.settings_clear_cache)) {
        clear(R.string.settings_confirm_clear_cache_body, viewModel::onClearCache)
    }
    TextRow(stringResource(R.string.settings_clear_history)) {
        clear(R.string.settings_confirm_clear_history_body, viewModel::onClearHistory)
    }
    TextRow(stringResource(R.string.settings_clear_bookmarks)) {
        clear(R.string.settings_confirm_clear_bookmarks_body, viewModel::onClearBookmarks)
    }
    TextRow(stringResource(R.string.settings_clear_trusted)) {
        clear(R.string.settings_confirm_clear_trusted_body, viewModel::onClearTrustedDomains)
    }
    TextRow(stringResource(R.string.settings_hidden_threads, state.hiddenThreads.size)) {
        showHidden = true
    }

    if (showHidden) {
        ManagedListDialog(
            title = stringResource(R.string.settings_hidden_threads, state.hiddenThreads.size),
            items = state.hiddenThreads,
            key = { it.board + "/" + it.threadNo },
            itemLabel = { "/${it.board}/${it.threadNo}" },
            removeLabel = stringResource(R.string.settings_unhide),
            onRemove = viewModel::onUnhideThread,
            onDismiss = { showHidden = false },
        )
    }
}
