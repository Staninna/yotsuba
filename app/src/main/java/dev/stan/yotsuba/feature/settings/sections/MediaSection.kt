package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.ChipRow
import dev.stan.yotsuba.domain.model.MediaAutoplay
import dev.stan.yotsuba.feature.settings.SettingsUiState
import dev.stan.yotsuba.feature.settings.SettingsViewModel
import dev.stan.yotsuba.feature.settings.labelRes

@Composable
fun MediaSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    ChipRow(
        label = stringResource(R.string.settings_media_autoplay),
        options = MediaAutoplay.entries,
        selected = state.settings.mediaAutoplay,
        onSelect = { v -> viewModel.update { it.copy(mediaAutoplay = v) } },
        labelOf = { stringResource(it.labelRes) },
    )
}
