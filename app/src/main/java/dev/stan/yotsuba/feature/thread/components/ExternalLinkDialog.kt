package dev.stan.yotsuba.feature.thread.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.network.LinkPreview
import dev.stan.yotsuba.core.util.Urls

/**
 * External-link confirmation (D26). The persistent choice is a checkbox in the body, not a
 * third button beside Cancel: Open honours it by calling [onTrustDomain], which both
 * remembers the domain and opens the link. The dialog exists to get consent before the app
 * talks to the host, so it fetches nothing on its own: the page's Open Graph summary only
 * loads when the reader taps Preview, which is itself a visit to the untrusted host.
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
    var previewing by rememberSaveable(url) { mutableStateOf(false) }
    val preview = if (previewing) rememberLinkPreview(url) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_dialog_title)) },
        text = {
            Column {
                Text(url)
                if (!previewing) {
                    TextButton(onClick = { previewing = true }, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.link_preview_show))
                    }
                } else if (preview != null) {
                    Spacer(Modifier.height(spacing.md))
                    if (preview.isEmpty) {
                        Text(
                            stringResource(R.string.link_preview_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    preview.siteName?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    preview.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    preview.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface LinkPreviewEntryPoint {
    fun linkPreview(): LinkPreview
}

/** Null until the fetch answers; only called once the reader has asked for a preview. */
@Composable
private fun rememberLinkPreview(url: String): LinkPreview.Preview? {
    val context: Context = LocalContext.current.applicationContext
    val previews = remember(context) {
        EntryPointAccessors.fromApplication(context, LinkPreviewEntryPoint::class.java).linkPreview()
    }
    return produceState<LinkPreview.Preview?>(initialValue = null, url) { value = previews.fetch(url) }.value
}
