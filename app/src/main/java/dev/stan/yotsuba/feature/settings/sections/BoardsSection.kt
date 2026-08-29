package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.TextRow
import dev.stan.yotsuba.feature.settings.SettingsViewModel

@Composable
fun BoardsSection(viewModel: SettingsViewModel, confirmThen: (Int, () -> Unit) -> Unit) {
    TextRow(
        title = stringResource(R.string.settings_hide_nsfw),
        summary = stringResource(R.string.settings_hide_nsfw_summary),
        onClick = {
            confirmThen(R.string.settings_confirm_hide_nsfw_body) { viewModel.onHideNsfwBoards() }
        },
    )
}
