package dev.stan.yotsuba.feature.thread.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.stan.yotsuba.R
import dev.stan.yotsuba.core.designsystem.token.LocalSpacing
import dev.stan.yotsuba.domain.model.ThreadPost

/** Quotelink preview card stack (D11) — overlay, level3 elevation. */
@Composable
fun QuotePreviewOverlay(
    group: List<ThreadPost>,
    onDismiss: () -> Unit,
    onGoTo: (Long) -> Unit,
    postCard: @Composable (ThreadPost) -> Unit,
) {
    val spacing = LocalSpacing.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier
                .padding(spacing.xl)
                .heightIn(max = 480.dp)
                .clickable(enabled = false) {},
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                group.forEach { post ->
                    Column {
                        postCard(post)
                        TextButton(onClick = { onGoTo(post.no) }, modifier = Modifier.align(Alignment.End)) {
                            Text(stringResource(R.string.thread_go_to_post))
                        }
                    }
                }
            }
        }
    }
}
