package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.ChipRow
import dev.stan.yotsuba.core.designsystem.component.SwitchRow
import dev.stan.yotsuba.domain.model.HistoryRetention
import dev.stan.yotsuba.domain.model.Settings
import dev.stan.yotsuba.feature.settings.labelRes

@Composable
fun ReadingSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    SwitchRow(
        title = stringResource(R.string.settings_auto_refresh),
        summary = stringResource(R.string.settings_auto_refresh_summary),
        checked = settings.autoRefreshEnabled,
        onToggle = { v -> update { it.copy(autoRefreshEnabled = v) } },
    )
    SwitchRow(
        title = stringResource(R.string.settings_record_history),
        summary = null,
        checked = settings.recordHistory,
        onToggle = { v -> update { it.copy(recordHistory = v) } },
    )
    ChipRow(
        label = stringResource(R.string.settings_history_retention),
        options = HistoryRetention.entries,
        selected = settings.historyRetention,
        onSelect = { v -> update { it.copy(historyRetention = v) } },
        labelOf = { stringResource(it.labelRes) },
    )
}
