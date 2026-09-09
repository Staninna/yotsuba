package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.ChipRow
import dev.stan.yotsuba.core.designsystem.component.SwitchRow
import dev.stan.yotsuba.domain.model.CatalogLayout
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.domain.model.ThemeMode
import dev.stan.yotsuba.feature.settings.labelRes

@Composable
fun AppearanceSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    ChipRow(
        label = stringResource(R.string.settings_theme),
        options = ThemeMode.entries,
        selected = settings.themeMode,
        onSelect = { mode -> update { it.copy(themeMode = mode) } },
        labelOf = { stringResource(it.labelRes) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_dynamic_color),
        summary = stringResource(R.string.settings_dynamic_color_summary),
        checked = settings.dynamicColor,
        onToggle = { v -> update { it.copy(dynamicColor = v) } },
    )
    SwitchRow(
        title = stringResource(R.string.settings_pure_black),
        summary = stringResource(R.string.settings_pure_black_summary),
        checked = settings.pureBlack,
        onToggle = { v -> update { it.copy(pureBlack = v) } },
        enabled = settings.themeMode != ThemeMode.LIGHT,
    )
    ChipRow(
        label = stringResource(R.string.settings_catalog_layout),
        options = CatalogLayout.entries,
        selected = settings.catalogLayout,
        onSelect = { layout -> update { it.copy(catalogLayout = layout) } },
        labelOf = { stringResource(it.labelRes) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_reveal_spoilers),
        summary = stringResource(R.string.settings_reveal_spoilers_summary),
        checked = settings.revealAllSpoilers,
        onToggle = { v -> update { it.copy(revealAllSpoilers = v) } },
    )
    SwitchRow(
        title = stringResource(R.string.settings_reduce_motion),
        summary = stringResource(R.string.settings_reduce_motion_summary),
        checked = settings.reduceMotion,
        onToggle = { v -> update { it.copy(reduceMotion = v) } },
    )
}
