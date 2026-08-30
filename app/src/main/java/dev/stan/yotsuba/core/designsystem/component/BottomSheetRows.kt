package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing

/** The title line at the top of an action sheet. */
@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = spacing.xl, vertical = spacing.sm),
    )
}

/**
 * One action in a bottom sheet: an icon, a label, and an optional line under it. A
 * disabled row greys both the icon and the label, keeping the sheet's shape.
 */
@Composable
fun SheetActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    ListItem(
        headlineContent = { Text(label, color = tint) },
        supportingContent = supporting?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        modifier = Modifier.combinedClickable(enabled = enabled, onClick = onClick),
    )
}

/** A dropdown entry with a string-resource label and an optional leading icon. */
@Composable
fun IconMenuItem(
    labelRes: Int,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
        enabled = enabled,
        onClick = onClick,
    )
}
