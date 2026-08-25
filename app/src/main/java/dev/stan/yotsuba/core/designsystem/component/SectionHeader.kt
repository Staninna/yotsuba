package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
    )
}
