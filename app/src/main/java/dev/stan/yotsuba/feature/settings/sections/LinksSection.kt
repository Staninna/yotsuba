package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.SwitchRow
import dev.stan.yotsuba.core.designsystem.component.TextRow
import dev.stan.yotsuba.feature.settings.ManagedListDialog
import dev.stan.yotsuba.feature.settings.SettingsUiState
import dev.stan.yotsuba.feature.settings.SettingsViewModel

@Composable
fun LinksSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val s = state.settings
    var showTrusted by remember { mutableStateOf(false) }

    SwitchRow(
        title = stringResource(R.string.settings_confirm_links),
        summary = stringResource(R.string.settings_confirm_links_summary),
        checked = s.confirmBeforeOpeningLinks,
        onToggle = { v -> viewModel.update { it.copy(confirmBeforeOpeningLinks = v) } },
    )
    TextRow(
        title = stringResource(R.string.settings_trusted_domains, s.trustedDomains.size),
        onClick = { showTrusted = true },
    )

    if (showTrusted) {
        ManagedListDialog(
            title = stringResource(R.string.settings_trusted_domains, s.trustedDomains.size),
            items = s.trustedDomains.sorted(),
            key = { it },
            itemLabel = { it },
            removeLabel = stringResource(R.string.action_remove),
            onRemove = viewModel::onRevokeTrustedDomain,
            onDismiss = { showTrusted = false },
        )
    }
}
