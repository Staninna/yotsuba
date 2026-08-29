package dev.stan.yotsuba.feature.thread.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import dev.stan.yotsuba.core.designsystem.theme.LocalYotsubaColors
import dev.stan.yotsuba.core.designsystem.theme.YotsubaColors
import dev.stan.yotsuba.core.text.PostAnnotation
import dev.stan.yotsuba.core.text.PostSegment
import dev.stan.yotsuba.core.text.PostStyle
import dev.stan.yotsuba.core.text.PostText

sealed interface BodyTap {
    data class SameThreadQuote(val postNo: Long) : BodyTap
    data class CrossThreadQuote(val board: String, val threadNo: Long, val postNo: Long?) : BodyTap
    data class Link(val url: String) : BodyTap
    data class Spoiler(val id: Int) : BodyTap
}

/**
 * @param onLongPress A held quotelink; null disables long-press handling entirely.
 */
@Composable
fun PostBody(
    body: PostText,
    revealedSpoilerIds: Set<Int>,
    revealAll: Boolean,
    onTap: (BodyTap) -> Unit,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    onLongPress: ((BodyTap) -> Unit)? = null,
    /** Text appended to a same-thread quotelink, keyed by the quoted post. */
    quoteLabels: Map<Long, String> = emptyMap(),
) {
    val colors = LocalYotsubaColors.current
    val scheme = MaterialTheme.colorScheme
    // The link listeners read the latest handler, so the string need not rebuild when it changes.
    val currentOnTap = rememberUpdatedState(onTap)
    val currentOnLongPress = rememberUpdatedState(onLongPress)
    val built = remember(body, revealedSpoilerIds, revealAll, highlight, quoteLabels, colors, scheme) {
        val taps = mutableListOf<Pair<IntRange, BodyTap>>()
        val annotated = buildAnnotatedString {
            body.segments.forEach { seg ->
                val spoilerId = seg.spoilerId
                val hiddenSpoiler = spoilerId != null && !revealAll && spoilerId !in revealedSpoilerIds
                val style = segmentStyle(seg, hiddenSpoiler, colors, scheme)
                val tap = tapFor(seg, hiddenSpoiler)
                val label = (seg.annotation as? PostAnnotation.QuotelinkSameThread)
                    ?.let { quoteLabels[it.postNo] }
                    ?.takeUnless { hiddenSpoiler }
                val text = if (label == null) seg.text else "${'$'}{seg.text} ${'$'}label"
                if (tap == null) {
                    withStyle(style) { append(text) }
                } else {
                    taps += (length until length + text.length) to tap
                    val link = LinkAnnotation.Clickable(
                        tag = text,
                        styles = TextLinkStyles(style = style),
                        linkInteractionListener = { currentOnTap.value(tap) },
                    )
                    withLink(link) { append(text) }
                }
            }
        }
        val display = if (!highlight.isNullOrBlank()) {
            highlightMatches(annotated, highlight, scheme.tertiaryContainer)
        } else annotated
        BuiltBody(display, taps)
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val longPressModifier = if (onLongPress == null) Modifier else Modifier.pointerInput(built) {
        detectTapGestures(onLongPress = { position ->
            val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
            val tap = built.taps.firstOrNull { offset in it.first }?.second ?: return@detectTapGestures
            currentOnLongPress.value?.invoke(tap)
        })
    }
    Text(
        text = built.display,
        style = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
        onTextLayout = { layout = it },
        modifier = modifier.then(longPressModifier),
    )
}

/** The rendered string and which character ranges are tappable, for long-press lookup. */
private class BuiltBody(val display: AnnotatedString, val taps: List<Pair<IntRange, BodyTap>>)

/** What a tap on [seg] means, or null when the run is inert. */
private fun tapFor(seg: PostSegment, hiddenSpoiler: Boolean): BodyTap? {
    // A hidden spoiler reveals first, even when it wraps a link or quotelink.
    if (hiddenSpoiler) return BodyTap.Spoiler(seg.spoilerId!!)
    return when (val a = seg.annotation) {
        is PostAnnotation.QuotelinkSameThread -> BodyTap.SameThreadQuote(a.postNo)
        is PostAnnotation.QuotelinkCrossThread -> BodyTap.CrossThreadQuote(a.board, a.threadNo, a.postNo)
        is PostAnnotation.Link -> BodyTap.Link(a.url)
        is PostAnnotation.Spoiler, PostAnnotation.Deadlink, null -> null
    }
}

private fun segmentStyle(
    seg: PostSegment,
    hiddenSpoiler: Boolean,
    colors: YotsubaColors,
    scheme: ColorScheme,
): SpanStyle {
    var style = SpanStyle()
    if (PostStyle.GREENTEXT in seg.styles) style = style.copy(color = colors.greentext)
    if (PostStyle.BOLD in seg.styles) style = style.copy(fontWeight = FontWeight.Bold)
    if (PostStyle.ITALIC in seg.styles) style = style.copy(fontStyle = FontStyle.Italic)
    if (PostStyle.UNDERLINE in seg.styles) style = style.copy(textDecoration = TextDecoration.Underline)
    if (PostStyle.CODE in seg.styles || PostStyle.SJIS in seg.styles || PostStyle.MATH in seg.styles) {
        style = style.copy(fontFamily = FontFamily.Monospace, background = scheme.surfaceVariant)
    }
    if (PostStyle.DEADLINK in seg.styles) {
        style = style.copy(color = colors.deadThread, textDecoration = TextDecoration.LineThrough)
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
    } else if (seg.spoilerId != null) {
        style = style.copy(background = scheme.surfaceVariant)
    }
    return style
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
