package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.ChipRow
import dev.stan.yotsuba.core.designsystem.component.SectionHeader
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

    SectionHeader(stringResource(R.string.settings_bookmarks))
    ChipRow(
        label = stringResource(R.string.settings_bookmark_refresh),
        options = (BookmarkRefreshOptions + settings.bookmarkRefreshMinutes).distinct().sorted(),
        selected = settings.bookmarkRefreshMinutes,
        onSelect = { v -> update { it.copy(bookmarkRefreshMinutes = v) } },
        labelOf = { stringResource(R.string.settings_bookmark_refresh_minutes, it) },
    )
    SwitchRow(
        title = stringResource(R.string.settings_bookmark_notifications),
        summary = stringResource(R.string.settings_bookmark_notifications_summary),
        checked = settings.bookmarkNotifications,
        onToggle = { v -> update { it.copy(bookmarkNotifications = v) } },
    )
}

/** The current value is included even if it is not one of these, so the chip row always has a selection. */
private val BookmarkRefreshOptions = listOf(15, 30, 60, 180)
