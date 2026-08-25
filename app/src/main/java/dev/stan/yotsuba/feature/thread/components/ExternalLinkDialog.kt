package dev.stan.yotsuba.feature.thread.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.util.Urls

/** External-link confirmation (D26). */
@Composable
fun ExternalLinkDialog(
    url: String,
    onOpen: () -> Unit,
    onTrustDomain: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_dialog_title)) },
        text = { Text(url) },
        confirmButton = {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.action_open)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onTrustDomain) {
                    Text(stringResource(R.string.link_always_trust, Urls.domainOf(url) ?: ""))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}
