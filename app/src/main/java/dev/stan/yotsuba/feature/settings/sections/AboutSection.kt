package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.NavigationRow
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing

@Composable
fun AboutSection(versionName: String, onOpenStats: () -> Unit) {
    val spacing = LocalSpacing.current
    Text(
        stringResource(R.string.settings_version, versionName),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
    )
    Text(
        stringResource(R.string.settings_attribution),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
    )
    NavigationRow(
        icon = Icons.Outlined.Insights,
        title = stringResource(R.string.stats_entry),
        summary = stringResource(R.string.stats_entry_summary),
        onClick = onOpenStats,
    )
}
