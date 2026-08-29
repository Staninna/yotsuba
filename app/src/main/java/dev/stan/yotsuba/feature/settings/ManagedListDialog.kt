package dev.stan.yotsuba.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.R

/** A dialog listing revocable entries — trusted domains, hidden threads — one remove per row. */
@Composable
internal fun <T> ManagedListDialog(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    itemLabel: (T) -> String,
    removeLabel: String,
    onRemove: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (items.isEmpty()) {
                Text(stringResource(R.string.settings_list_empty))
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(items.size, key = { key(items[it]) }) { i ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(itemLabel(items[i]), Modifier.weight(1f))
                            TextButton(onClick = { onRemove(items[i]) }) {
                                Text(removeLabel)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}
