package dev.stan.yotsuba.feature.thread.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.util.Urls

/**
 * External-link confirmation (D26). The persistent choice is a checkbox in the body, not a
 * third button beside Cancel: Open honours it by calling [onTrustDomain], which both
 * remembers the domain and opens the link.
 */
@Composable
fun ExternalLinkDialog(
    url: String,
    onOpen: () -> Unit,
    onTrustDomain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var trust by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_dialog_title)) },
        text = {
            Column {
                Text(url)
                Spacer(Modifier.height(spacing.md))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = trust, role = Role.Checkbox, onValueChange = { trust = it }),
                ) {
                    Checkbox(checked = trust, onCheckedChange = null)
                    Spacer(Modifier.width(spacing.sm))
                    Text(stringResource(R.string.link_always_trust, Urls.domainOf(url) ?: ""))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = if (trust) onTrustDomain else onOpen) { Text(stringResource(R.string.action_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
