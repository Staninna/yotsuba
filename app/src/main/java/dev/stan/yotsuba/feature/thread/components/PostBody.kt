package dev.stan.yotsuba.feature.thread.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import dev.stan.yotsuba.core.designsystem.theme.LocalYotsubaColors
import dev.stan.yotsuba.core.text.PostAnnotation
import dev.stan.yotsuba.core.text.PostStyle
import dev.stan.yotsuba.core.text.PostText

sealed interface BodyTap {
    data class SameThreadQuote(val postNo: Long) : BodyTap
    data class CrossThreadQuote(val board: String, val threadNo: Long, val postNo: Long?) : BodyTap
    data class Link(val url: String) : BodyTap
    data class Spoiler(val id: Int) : BodyTap
}

private const val TAG = "yotsuba"

@Composable
fun PostBody(
    body: PostText,
    revealedSpoilerIds: Set<Int>,
    revealAll: Boolean,
    onTap: (BodyTap) -> Unit,
    modifier: Modifier = Modifier,
    highlight: String? = null,
) {
    val colors = LocalYotsubaColors.current
    val scheme = MaterialTheme.colorScheme
    val annotated = buildAnnotatedString {
        body.segments.forEachIndexed { index, seg ->
            val spoilerId = seg.spoilerId
            val hiddenSpoiler = spoilerId != null && !revealAll && spoilerId !in revealedSpoilerIds
            var style = SpanStyle()
            if (PostStyle.GREENTEXT in seg.styles) style = style.copy(color = colors.greentext)
            if (PostStyle.BOLD in seg.styles) style = style.copy(fontWeight = FontWeight.Bold)
            if (PostStyle.ITALIC in seg.styles) style = style.copy(fontStyle = FontStyle.Italic)
            if (PostStyle.UNDERLINE in seg.styles) style = style.copy(textDecoration = TextDecoration.Underline)
            if (PostStyle.CODE in seg.styles || PostStyle.SJIS in seg.styles || PostStyle.MATH in seg.styles) {
                style = style.copy(fontFamily = FontFamily.Monospace, background = scheme.surfaceVariant)
            }
            if (PostStyle.DEADLINK in seg.styles) {
                style = style.copy(
                    color = colors.deadThread,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
            when (seg.annotation) {
                is PostAnnotation.QuotelinkSameThread,
                is PostAnnotation.QuotelinkCrossThread ->
                    style = style.copy(color = colors.quotelink, textDecoration = TextDecoration.Underline)
                is PostAnnotation.Link ->
                    style = style.copy(color = scheme.primary, textDecoration = TextDecoration.Underline)
                else -> {}
            }
            if (hiddenSpoiler) {
                style = style.copy(color = colors.spoilerScrim, background = colors.spoilerScrim)
            } else if (spoilerId != null) {
                style = style.copy(background = scheme.surfaceVariant)
            }
            pushStringAnnotation(TAG, index.toString())
            withStyle(style) { append(seg.text) }
            pop()
        }
    }
    val display = if (!highlight.isNullOrBlank()) {
        highlightMatches(annotated, highlight, scheme.tertiaryContainer)
    } else annotated
    ClickableText(
        text = display,
        style = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
        modifier = modifier,
        onClick = { offset ->
            val segIndex = display.getStringAnnotations(TAG, offset, offset)
                .firstOrNull()?.item?.toIntOrNull() ?: return@ClickableText
            val seg = body.segments.getOrNull(segIndex) ?: return@ClickableText
            val spoilerId = seg.spoilerId
            if (spoilerId != null && !revealAll && spoilerId !in revealedSpoilerIds) {
                // A hidden spoiler reveals first, even when it wraps a link or quotelink.
                onTap(BodyTap.Spoiler(spoilerId))
                return@ClickableText
            }
            when (val a = seg.annotation) {
                is PostAnnotation.Spoiler -> {} // already revealed
                is PostAnnotation.QuotelinkSameThread -> onTap(BodyTap.SameThreadQuote(a.postNo))
                is PostAnnotation.QuotelinkCrossThread ->
                    onTap(BodyTap.CrossThreadQuote(a.board, a.threadNo, a.postNo))
                is PostAnnotation.Link -> onTap(BodyTap.Link(a.url))
                PostAnnotation.Deadlink, null -> {} // inert
            }
        },
    )
}

private fun highlightMatches(source: AnnotatedString, query: String, background: Color): AnnotatedString {
    val text = source.text
    var start = 0
    val builder = AnnotatedString.Builder(source)
    while (true) {
        val idx = text.indexOf(query, start, ignoreCase = true)
        if (idx == -1) break
        builder.addStyle(SpanStyle(background = background), idx, idx + query.length)
        start = idx + query.length
    }
    return builder.toAnnotatedString()
}
