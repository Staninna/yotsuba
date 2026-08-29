package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.ChipRow
import dev.stan.yotsuba.core.designsystem.component.SwitchRow
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.feature.settings.SettingsUiState
import dev.stan.yotsuba.feature.settings.SettingsViewModel
import dev.stan.yotsuba.feature.settings.labelRes

@Composable
fun ReadingSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val s = state.settings
    SwitchRow(
        title = stringResource(R.string.settings_auto_refresh),
        summary = stringResource(R.string.settings_auto_refresh_summary),
        checked = s.autoRefreshEnabled,
        onToggle = { v -> viewModel.update { it.copy(autoRefreshEnabled = v) } },
    )
    SwitchRow(
        title = stringResource(R.string.settings_record_history),
        summary = null,
        checked = s.recordHistory,
        onToggle = { v -> viewModel.update { it.copy(recordHistory = v) } },
    )
    ChipRow(
        label = stringResource(R.string.settings_history_retention),
        options = HistoryRetention.entries,
        selected = s.historyRetention,
        onSelect = { v -> viewModel.update { it.copy(historyRetention = v) } },
        labelOf = { stringResource(it.labelRes) },
    )
}
