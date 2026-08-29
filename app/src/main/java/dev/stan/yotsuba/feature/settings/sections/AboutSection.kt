package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.feature.settings.SettingsUiState

@Composable
fun AboutSection(state: SettingsUiState) {
    val spacing = LocalSpacing.current
    Text(
        stringResource(R.string.settings_version, state.versionName),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
    )
    Text(
        stringResource(R.string.settings_attribution),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
    )
}
