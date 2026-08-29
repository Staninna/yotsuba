package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.ChipRow
import dev.stan.yotsuba.core.designsystem.component.SwitchRow
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.feature.settings.SettingsUiState
import dev.stan.yotsuba.feature.settings.SettingsViewModel
import dev.stan.yotsuba.feature.settings.labelRes

@Composable
fun AppearanceSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val s = state.settings
    ChipRow(
        label = stringResource(R.string.settings_theme),
        options = ThemeMode.entries,
        selected = s.themeMode,
        onSelect = { mode -> viewModel.update { it.copy(themeMode = mode) } },
        labelOf = { stringResource(it.labelRes) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_dynamic_color),
        summary = stringResource(R.string.settings_dynamic_color_summary),
        checked = s.dynamicColor,
        onToggle = { v -> viewModel.update { it.copy(dynamicColor = v) } },
    )
    ChipRow(
        label = stringResource(R.string.settings_catalog_layout),
        options = CatalogLayout.entries,
        selected = s.catalogLayout,
        onSelect = { layout -> viewModel.update { it.copy(catalogLayout = layout) } },
        labelOf = { stringResource(it.labelRes) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_reveal_spoilers),
        summary = stringResource(R.string.settings_reveal_spoilers_summary),
        checked = s.revealAllSpoilers,
        onToggle = { v -> viewModel.update { it.copy(revealAllSpoilers = v) } },
    )
}
