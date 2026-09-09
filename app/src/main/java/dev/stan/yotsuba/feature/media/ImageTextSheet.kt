package dev.stan.yotsuba.feature.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.component.SheetTitle
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.media.ImageText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs text recognition over the image [file] and shows what it found, selectable, with
 * a copy button for the lot. Nothing is kept: dismiss and the text is gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageTextSheet(file: File, onDismiss: () -> Unit, onCopied: () -> Unit) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val clipboard = LocalClipboardManager.current
    // Optional-of-optional: the outer null is "still working", the inner is "found nothing".
    val result by produceState<String?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { ImageText.recognise(context, file) } ?: ""
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = spacing.xl)) {
            SheetTitle(stringResource(R.string.media_text_title))
            when (val text = result) {
                null -> Box(
                    Modifier.fillMaxWidth().padding(spacing.xl),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                "" -> Text(
                    stringResource(R.string.media_text_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.xl),
                )
                else -> {
                    SelectionContainer(
                        Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = spacing.xl),
                    ) {
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = spacing.xl, vertical = spacing.sm),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = {
                            clipboard.setText(AnnotatedString(text))
                            onCopied()
                            onDismiss()
                        }) {
                            Text(stringResource(R.string.media_text_copy))
                        }
                    }
                }
            }
        }
    }
}
