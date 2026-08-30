package dev.stan.yotsuba.feature.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.core.update.ReleaseNotes

/** The release body, laid out as the sections and bullets bump.sh writes. */
@Composable
internal fun ReleaseNotesView(markdown: String, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    val notes = remember(markdown) { ReleaseNotes.parse(markdown) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().padding(top = spacing.sm)) {
        notes.sections.forEach { section ->
            section.title?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = spacing.sm, bottom = spacing.xs),
                )
            }
            section.items.forEach { item ->
                Row(Modifier.padding(bottom = spacing.xs)) {
                    Text("•", style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.width(spacing.md))
                    Text(
                        buildAnnotatedString {
                            item.lead?.let {
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(it) }
                                append(" — ")
                            }
                            append(item.text)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
            }
        }
    }
}
