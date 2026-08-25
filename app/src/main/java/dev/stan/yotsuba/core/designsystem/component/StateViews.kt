package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.NetworkError
import dev.stan.yotsuba.core.util.UiState

/** The shared screen shell: skeleton while loading, error with retry, otherwise [content]. */
@Composable
fun <T> UiStateContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        UiState.Loading -> LoadingSkeleton(modifier)
        is UiState.Error -> ErrorState(state.error, onRetry, modifier)
        is UiState.Success -> content(state.data)
    }
}

@Composable
fun EmptyState(
    title: String,
    explanation: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.SearchOff,
    action: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier.fillMaxSize().padding(spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(spacing.lg))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(spacing.sm))
        Text(
            explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(spacing.lg))
            action()
        }
    }
}

/** The shared "no matches for your query" empty state. */
@Composable
fun NoSearchResults(query: String, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.search_no_results_title),
        explanation = stringResource(R.string.search_no_results_explanation, query),
        modifier = modifier,
    )
}

/** The shared single-line inline search field. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hintRes: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(hintRes)) },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
fun ErrorState(
    error: NetworkError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, message) = errorPresentation(error)
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier.fillMaxSize().padding(spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(spacing.lg))
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(spacing.lg))
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun errorPresentation(error: NetworkError): Pair<ImageVector, String> = when (error) {
    NetworkError.Offline -> Icons.Filled.WifiOff to stringResource(R.string.error_offline)
    NetworkError.Timeout -> Icons.Filled.HourglassEmpty to stringResource(R.string.error_timeout)
    NetworkError.RateLimited -> Icons.Filled.HourglassEmpty to stringResource(R.string.error_rate_limited)
    NetworkError.NotFound -> Icons.Filled.ErrorOutline to stringResource(R.string.error_not_found)
    is NetworkError.Server -> Icons.Filled.CloudOff to stringResource(R.string.error_server, error.code)
    is NetworkError.Unknown -> Icons.Filled.ErrorOutline to stringResource(R.string.error_unknown)
}

@Composable
fun OfflineBanner(cachedAtLabel: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(spacing.sm))
            Text(
                if (cachedAtLabel != null) {
                    stringResource(R.string.offline_showing_cached, cachedAtLabel)
                } else {
                    stringResource(R.string.offline)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier, rows: Int = 8) {
    val spacing = LocalSpacing.current
    Column(modifier = modifier.fillMaxSize().padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        repeat(rows) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.medium,
                    )
            )
        }
    }
}
