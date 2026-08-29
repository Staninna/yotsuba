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
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.feature.settings.ManagedListDialog

@Composable
fun LinksSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    var showTrusted by remember { mutableStateOf(false) }

    SwitchRow(
        title = stringResource(R.string.settings_confirm_links),
        summary = stringResource(R.string.settings_confirm_links_summary),
        checked = settings.confirmBeforeOpeningLinks,
        onToggle = { v -> update { it.copy(confirmBeforeOpeningLinks = v) } },
    )
    TextRow(
        title = stringResource(R.string.settings_trusted_domains, settings.trustedDomains.size),
        onClick = { showTrusted = true },
    )

    if (showTrusted) {
        ManagedListDialog(
            title = stringResource(R.string.settings_trusted_domains, settings.trustedDomains.size),
            items = settings.trustedDomains.sorted(),
            key = { it },
            itemLabel = { it },
            removeLabel = stringResource(R.string.action_remove),
            onRemove = { domain -> update { it.copy(trustedDomains = it.trustedDomains - domain) } },
            onDismiss = { showTrusted = false },
        )
    }
}
