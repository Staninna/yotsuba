package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing

/**
 * The settings vocabulary, shared by the index and every subscreen. Kept here rather than
 * in the settings feature so a subscreen in its own file does not have to reach sideways
 * into a sibling for its rows.
 */

/** M3's disabled content alpha; a disabled row dims its text to match its dead control. */
private const val DISABLED_ALPHA = 0.38f

private fun Color.disabledIf(enabled: Boolean): Color = if (enabled) this else copy(alpha = DISABLED_ALPHA)

/**
 * One toggleable node for TalkBack ("title, switch, on"), so the row and its Switch are not
 * announced as two unrelated controls; the Switch itself is display-only.
 */
@Composable
fun SwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.disabledIf(enabled),
            )
            summary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.disabledIf(enabled),
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun TextRow(title: String, summary: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.disabledIf(enabled))
        summary?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.disabledIf(enabled),
            )
        }
    }
}

/** A row that opens a subscreen: leading icon, title, current-value summary, chevron. */
@Composable
fun NavigationRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    labelOf: @Composable (T) -> String,
) {
    val spacing = LocalSpacing.current
    Column(Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.disabledIf(enabled),
        )
        FlowRow {
            options.forEach { value ->
                androidx.compose.material3.FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    enabled = enabled,
                    label = { Text(labelOf(value)) },
                    modifier = Modifier.padding(end = spacing.sm),
                )
            }
        }
    }
}
