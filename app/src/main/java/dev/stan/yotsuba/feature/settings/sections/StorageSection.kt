package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.SwitchRow
import dev.stan.yotsuba.core.designsystem.component.TextRow
import dev.stan.yotsuba.domain.model.Settings

@Composable
fun StorageSection(
    settings: Settings,
    update: ((Settings) -> Settings) -> Unit,
    onClearCache: () -> Unit,
    onClearHistory: () -> Unit,
    onClearBookmarks: () -> Unit,
    confirmThen: (Int, () -> Unit) -> Unit,
    showMessage: (String) -> Unit,
) {
    val cleared = stringResource(R.string.settings_cleared)

    fun clear(bodyRes: Int, action: () -> Unit) = confirmThen(bodyRes) {
        action()
        showMessage(cleared)
    }

    TextRow(stringResource(R.string.settings_clear_cache)) {
        clear(R.string.settings_confirm_clear_cache_body, onClearCache)
    }
    TextRow(stringResource(R.string.settings_clear_history)) {
        clear(R.string.settings_confirm_clear_history_body, onClearHistory)
    }
    TextRow(stringResource(R.string.settings_clear_bookmarks)) {
        clear(R.string.settings_confirm_clear_bookmarks_body, onClearBookmarks)
    }
    TextRow(stringResource(R.string.settings_clear_trusted)) {
        clear(R.string.settings_confirm_clear_trusted_body) { update { it.copy(trustedDomains = emptySet()) } }
    }
    SwitchRow(
        title = stringResource(R.string.settings_confirm_vault_delete),
        summary = stringResource(R.string.settings_confirm_vault_delete_summary),
        checked = settings.confirmVaultDelete,
        onToggle = { v -> update { it.copy(confirmVaultDelete = v) } },
    )
}
