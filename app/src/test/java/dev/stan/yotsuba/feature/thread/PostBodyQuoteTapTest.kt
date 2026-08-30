package dev.stan.yotsuba.feature.thread

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.stan.yotsuba.core.designsystem.theme.LocalPostTypography
import dev.stan.yotsuba.domain.model.PostAnnotation
import dev.stan.yotsuba.domain.model.PostSegment
import dev.stan.yotsuba.domain.model.PostStyle
import dev.stan.yotsuba.domain.model.PostText
import dev.stan.yotsuba.feature.thread.components.BodyTap
import dev.stan.yotsuba.feature.thread.components.PostBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * A tap on a `>>123` quotelink must reach [PostBody]'s `onTap` as a [BodyTap.SameThreadQuote],
 * with the long-press detector that shares the same text in place. The taps go through the
 * window as raw motion events, so the whole Compose pointer pipeline is under test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PostBodyQuoteTapTest {

    private val body = PostText(
        listOf(
            PostSegment(">>123", annotation = PostAnnotation.QuotelinkSameThread(123)),
            PostSegment("\nsome reply text that follows the quotelink"),
        ),
    )

    @Test
    fun `tapping a quotelink fires SameThreadQuote with the long-press detector installed`() =
        tapQuotelink(withLongPress = true)

    @Test
    fun `tapping a quotelink fires SameThreadQuote without a long-press handler`() =
        tapQuotelink(withLongPress = false)

    /** A `>>123` deadlink is tappable once the parser found its number; a cross-board one is not. */
    @Test
    fun `tapping a numbered deadlink fires Deadlink and an unnumbered one is inert`() {
        val numbered = PostText(listOf(PostSegment(">>123", styles = setOf(PostStyle.DEADLINK), annotation = PostAnnotation.Deadlink(123))))
        assertEquals(listOf<BodyTap>(BodyTap.Deadlink(123)), tapsOn(numbered))
        val crossBoard = PostText(listOf(PostSegment(">>>/a/123", styles = setOf(PostStyle.DEADLINK), annotation = PostAnnotation.Deadlink(null))))
        assertEquals(emptyList<BodyTap>(), tapsOn(crossBoard))
    }

    private fun tapsOn(text: PostText): List<BodyTap> {
        val taps = mutableListOf<BodyTap>()
        var bounds: Rect? = null
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            PlainBodyTheme {
                PostBody(
                    body = text,
                    revealedSpoilerIds = emptySet(),
                    revealAll = false,
                    onTap = { taps += it },
                    modifier = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
                )
            }
        }
        ShadowLooper.idleMainLooper()
        val rect = bounds ?: error("body never laid out")
        tap(activity, rect.left + 2f, rect.top + 10f)
        ShadowLooper.idleMainLooper()
        return taps
    }

    /** Sanity check on the harness: a plain clickable box must see the same synthetic tap. */
    @Test
    fun `the synthetic tap reaches a plain clickable`() {
        var clicks = 0
        var bounds: Rect? = null
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            Box(
                Modifier.size(100.dp).clickable { clicks++ }
                    .onGloballyPositioned { bounds = it.boundsInWindow() },
            )
        }
        ShadowLooper.idleMainLooper()
        val rect = bounds ?: error("box never laid out")
        tap(activity, rect.left + 10f, rect.top + 10f)
        ShadowLooper.idleMainLooper()
        assertEquals(1, clicks)
    }

    private fun tapQuotelink(withLongPress: Boolean) {
        val taps = mutableListOf<BodyTap>()
        val holds = mutableListOf<BodyTap>()
        var bounds: Rect? = null
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            PlainBodyTheme {
                PostBody(
                    body = body,
                    revealedSpoilerIds = emptySet(),
                    revealAll = false,
                    onTap = { taps += it },
                    onLongPress = if (withLongPress) { t -> holds += t } else null,
                    modifier = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
                )
            }
        }
        ShadowLooper.idleMainLooper()
        val rect = bounds ?: error("body never laid out")
        // The quotelink is the first run on the first line. Robolectric's legacy text stack
        // measures a run a few pixels wide, so the tap sits just inside the top-left corner.
        tap(activity, rect.left + 2f, rect.top + 10f)
        ShadowLooper.idleMainLooper()

        assertEquals(listOf<BodyTap>(BodyTap.SameThreadQuote(123)), taps)
        assertEquals(emptyList<BodyTap>(), holds)
    }

    /**
     * Robolectric's legacy text stack turns Material 3's default body style (a `lineHeight`
     * with a `lineHeightStyle`) into an empty selection path, and Compose clips each link's
     * hit box to that path. A plain body style keeps the link tappable under the JVM; on a
     * device the M3 style is fine.
     */
    @Composable
    private fun PlainBodyTheme(content: @Composable () -> Unit) {
        val plain = Typography(bodyMedium = TextStyle(fontSize = 14.sp))
        // PostBody reads the post typography local, not MaterialTheme, so override both.
        CompositionLocalProvider(LocalPostTypography provides plain) {
            MaterialTheme(typography = plain, content = content)
        }
    }

    private fun tap(activity: ComponentActivity, x: Float, y: Float) {
        val view = activity.window.decorView
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        view.dispatchTouchEvent(down)
        down.recycle()
        val up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, x, y, 0)
        view.dispatchTouchEvent(up)
        up.recycle()
    }
}
